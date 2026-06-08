package com.zjkl.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.config.properties.MilvusProperties;
import com.zjkl.common.util.MilvusQueryUtil;
import com.zjkl.memory.config.GraphMilvusCollectionManager;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GraphQueryService {

    /**
     * 图谱查询结果（含格式化文本块和相关度评分，用于跨路 RAG 融合排序）
     */
    public record GraphResult(String block, double topScore) {
        public static GraphResult empty() { return new GraphResult("", 0.0); }
    }

    private final MilvusClientV2 milvusClientV2;
    private final MilvusProperties milvusProperties;
    private final EmbeddingModel embeddingModel;
    private final QwenChatModel qwenChatModel;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final GraphMilvusCollectionManager graphMilvusCollectionManager;

    // ========== 查询参数常量（消除魔法数字） ==========
    /** 实体提取：LLM 识别后去重截断上限 */
    private static final int ENTITY_EXTRACT_LIMIT = 5;
    /** 实体搜索：Milvus 每向量 topK */
    private static final int ENTITY_SEARCH_TOP_K = 5;
    /** 实体搜索：排序后截断上限 */
    private static final int ENTITY_RESULT_LIMIT = 6;
    /** 关系搜索：Milvus 语义检索 topK */
    private static final int RELATION_SEARCH_TOP_K = 12;
    /** 关系重排：LLM 重排后截断上限 */
    private static final int RERANK_RESULT_LIMIT = 5;
    /** 格式化输出：实体展示上限 */
    private static final int ENTITY_DISPLAY_LIMIT = 3;
    /** 格式化输出：关系展示上限 */
    private static final int RELATION_DISPLAY_LIMIT = 5;
    /** 格式化输出：来源片段展示上限 */
    private static final int PASSAGE_DISPLAY_LIMIT = 3;
    /** ID 扩展后的候选关系上限，避免 prompt 过大 */
    private static final int CANDIDATE_RELATION_LIMIT = 40;

    /** 实体文本安全校验：仅允许字母、数字和空格，防止 Milvus filter 注入 */
    private static final Pattern SAFE_ENTITY_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s]+$");

    private String validateEntityText(String text) {
        if (text == null || !SAFE_ENTITY_PATTERN.matcher(text).matches()) {
            throw new IllegalArgumentException("Invalid entity text: contains unsafe characters");
        }
        return text;
    }

    public GraphQueryService(MilvusClientV2 milvusClientV2,
                             MilvusProperties milvusProperties,
                              EmbeddingModel embeddingModel,
                              QwenChatModel qwenChatModel,
                              PromptTemplateService promptTemplateService,
                              ObjectMapper objectMapper,
                              GraphMilvusCollectionManager graphMilvusCollectionManager) {
        this.milvusClientV2 = milvusClientV2;
        this.milvusProperties = milvusProperties;
        this.embeddingModel = embeddingModel;
        this.qwenChatModel = qwenChatModel;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
        this.graphMilvusCollectionManager = graphMilvusCollectionManager;
    }

    public GraphResult buildGraphBlock(String userId, String question) {
        if (!graphMilvusCollectionManager.isCollectionReady()) {
            log.debug("图谱集合未就绪，跳过 graph RAG: userId={}", userId);
            return GraphResult.empty();
        }
        if (question == null || question.isBlank()) {
            return GraphResult.empty();
        }

        try {
            return doBuildGraphBlock(userId, question);
        } catch (Exception e) {
            log.warn("graph RAG 检索失败，跳过图谱上下文: userId={}", userId, e);
            return GraphResult.empty();
        }
    }

    private GraphResult doBuildGraphBlock(String userId, String question) {

        List<String> queryEntities = extractEntities(question);
        List<Map<String, Object>> matchedEntities = queryEntities.isEmpty()
                ? List.of()
                : searchEntities(userId, queryEntities);
        // 取 top 实体分数作为图谱检索相关度评分（用于跨路 RAG 融合排序）
        double topScore = matchedEntities.isEmpty() ? 0.0
                : (matchedEntities.get(0).get("score") instanceof Number n ? n.doubleValue() : 0.0);

        Set<String> entityTexts = matchedEntities.stream()
                .map(row -> row.get("text"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> candidateRelations = collectCandidateRelations(userId, question, matchedEntities, entityTexts);
        if (candidateRelations.isEmpty()) {
            if (matchedEntities.isEmpty()) {
                log.debug("图查询未命中实体和关系 userId={}, question={}", userId, question);
                return GraphResult.empty();
            }
            return new GraphResult(formatEntityOnlyBlock(matchedEntities), topScore);
        }
        if (topScore == 0.0 && candidateRelations.get(0).get("score") instanceof Number n) {
            topScore = n.doubleValue();
        }

        List<Map<String, Object>> reranked = rerankRelations(question, candidateRelations);
        log.debug("图查询命中 userId={}, entities={}, candidateRelations={}, reranked={}",
                userId, matchedEntities.size(), candidateRelations.size(), reranked.size());
        List<Map<String, Object>> finalRelations = reranked.isEmpty() ? candidateRelations : reranked;
        List<Map<String, Object>> passages = fetchPassagesByRelations(userId, finalRelations);
        return new GraphResult(formatGraphBlock(matchedEntities, finalRelations, passages), topScore);
    }

    private List<String> extractEntities(String question) {
        String prompt = promptTemplateService.render("graph-query-entities", Map.of("question", question));
        try {
            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from("你是一个实体识别器，只输出 JSON。"),
                            UserMessage.from(prompt)
                    )
                    .build());
            String text = response.aiMessage() != null ? response.aiMessage().text() : null;
            if (text == null || text.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(MilvusQueryUtil.extractJson(text));
            JsonNode entitiesNode = root.path("entities");
            if (!entitiesNode.isArray()) {
                return List.of();
            }
            List<String> entities = new ArrayList<>();
            for (JsonNode entity : entitiesNode) {
                String value = MilvusQueryUtil.normalizePhrase(entity.asText());
                if (!value.isBlank()) {
                    entities.add(value);
                }
            }
            return entities.stream().distinct().limit(ENTITY_EXTRACT_LIMIT).toList();
        } catch (Exception e) {
            log.warn("图查询实体识别失败", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> searchEntities(String userId, List<String> entities) {
        List<Map<String, Object>> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 批量 embedding，避免 N+1 调用
        List<dev.langchain4j.data.segment.TextSegment> segments = entities.stream()
                .map(dev.langchain4j.data.segment.TextSegment::from)
                .toList();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 单次 Milvus 多向量查询替代 N 次单独查询
        List<io.milvus.v2.service.vector.request.data.BaseVector> queryVectors = new ArrayList<>();
        for (Embedding embedding : embeddings) {
            embedding.normalize();
            queryVectors.add(new FloatVec(toFloatList(embedding.vector())));
        }

        SearchReq req = SearchReq.builder()
                .collectionName(milvusProperties.getGraphEntityCollectionName())
                .data(queryVectors)
                .topK(ENTITY_SEARCH_TOP_K)
                .filter(MilvusQueryUtil.userFilter(userId))
                .outputFields(List.of("id", "text", "type", "mention_count", "last_seen", "relation_ids", "passage_ids"))
                .build();
        SearchResp resp = milvusClientV2.search(req);
        if (resp.getSearchResults() != null) {
            for (List<SearchResp.SearchResult> list : resp.getSearchResults()) {
                for (SearchResp.SearchResult item : list) {
                    String id = item.getId().toString();
                    if (seen.add(id)) {
                        Map<String, Object> row = new HashMap<>(item.getEntity());
                        row.put("id", id);
                        row.put("score", item.getScore());
                        results.add(row);
                    }
                }
            }
        }

        return results.stream()
                .sorted(Comparator.comparingDouble((Map<String, Object> row) -> row.get("score") instanceof Number n ? n.doubleValue() : 0.0).reversed())
                .limit(ENTITY_RESULT_LIMIT)
                .toList();
    }

    private List<Map<String, Object>> collectCandidateRelations(String userId, String question,
                                                                List<Map<String, Object>> matchedEntities,
                                                                Set<String> entityTexts) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        Set<String> relationIds = matchedEntities.stream()
                .map(row -> row.get("relation_ids"))
                .flatMap(value -> parseJsonList(value).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        queryByIds(milvusProperties.getGraphRelationCollectionName(), relationIds, userId,
                List.of("id", "text", "subject", "predicate", "object", "relation_type", "confidence", "timestamp", "entity_ids", "passage_ids", "source_id"))
                .forEach(row -> putById(merged, row));

        if (!entityTexts.isEmpty()) {
            String relationFilter = MilvusQueryUtil.userFilter(userId) + " and (" + entityTexts.stream()
                    .map(text -> {
                        validateEntityText(text);
                        return "subject == \"" + MilvusQueryUtil.escape(text) + "\" or object == \"" + MilvusQueryUtil.escape(text) + "\"";
                    })
                    .collect(Collectors.joining(" or ")) + ")";
            for (Map<String, Object> relation : MilvusQueryUtil.queryByFilter(milvusClientV2,
                    milvusProperties.getGraphRelationCollectionName(),
                    relationFilter,
                    List.of("id", "text", "subject", "predicate", "object", "relation_type", "confidence", "timestamp", "entity_ids", "passage_ids", "source_id")
            )) {
                putById(merged, relation);
            }
        }

        Embedding embedding = embeddingModel.embed(question).content();
        embedding.normalize();
        SearchReq req = SearchReq.builder()
                .collectionName(milvusProperties.getGraphRelationCollectionName())
                .data(List.of(new FloatVec(toFloatList(embedding.vector()))))
                .topK(RELATION_SEARCH_TOP_K)
                .filter(MilvusQueryUtil.userFilter(userId))
                .outputFields(List.of("id", "text", "subject", "predicate", "object", "relation_type", "confidence", "timestamp", "entity_ids", "passage_ids", "source_id"))
                .build();
        SearchResp resp = milvusClientV2.search(req);
        if (resp.getSearchResults() != null) {
            for (List<SearchResp.SearchResult> list : resp.getSearchResults()) {
                for (SearchResp.SearchResult item : list) {
                    Map<String, Object> row = new HashMap<>(item.getEntity());
                    row.put("id", item.getId().toString());
                    row.put("score", item.getScore());
                    putById(merged, row);
                }
            }
        }

        return new ArrayList<>(merged.values()).stream()
                .limit(CANDIDATE_RELATION_LIMIT)
                .toList();
    }

    private List<Map<String, Object>> rerankRelations(String question, List<Map<String, Object>> relations) {
        if (relations.isEmpty()) {
            return List.of();
        }

        String relationsText = relations.stream()
                .map(row -> row.get("id") + " => " + row.get("text"))
                .collect(Collectors.joining("\n"));
        String prompt = promptTemplateService.render("graph-rerank", Map.of(
                "question", question,
                "relations", relationsText
        ));
        try {
            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from("你是一个图关系重排器，只输出 JSON。"),
                            UserMessage.from(prompt)
                    )
                    .build());
            String text = response.aiMessage() != null ? response.aiMessage().text() : null;
            if (text == null || text.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(MilvusQueryUtil.extractJson(text));
            JsonNode idsNode = root.path("useful_relation_ids");
            if (!idsNode.isArray()) {
                return List.of();
            }
            Map<String, Map<String, Object>> byId = relations.stream()
                    .collect(Collectors.toMap(row -> row.get("id").toString(), row -> row, (a, b) -> a, LinkedHashMap::new));
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                String id = idNode.asText();
                Map<String, Object> row = byId.get(id);
                if (row != null) {
                    ranked.add(row);
                }
            }
            return ranked.stream().limit(RERANK_RESULT_LIMIT).toList();
        } catch (Exception e) {
            log.warn("图关系 rerank 失败", e);
            return List.of();
        }
    }

    private String formatEntityOnlyBlock(List<Map<String, Object>> entities) {
        String joined = entities.stream()
                .limit(ENTITY_DISPLAY_LIMIT)
                .map(row -> "- " + row.get("text") + "（" + row.get("type") + "）")
                .collect(Collectors.joining("\n"));
        return joined.isBlank() ? "" : "【图谱实体上下文】\n" + joined;
    }

    private List<Map<String, Object>> fetchPassagesByRelations(String userId, List<Map<String, Object>> relations) {
        Set<String> passageIds = relations.stream()
                .flatMap(row -> {
                    List<String> ids = parseJsonList(row.get("passage_ids"));
                    if (!ids.isEmpty()) {
                        return ids.stream();
                    }
                    Object sourceId = row.get("source_id");
                    return sourceId == null ? java.util.stream.Stream.<String>empty() : java.util.stream.Stream.of(sourceId.toString());
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (passageIds.isEmpty()) {
            return List.of();
        }
        return queryByIds(milvusProperties.getGraphPassageCollectionName(), passageIds, userId,
                List.of("id", "user_id", "text", "source_type", "source_ref_id", "timestamp", "entity_ids", "relation_ids"))
                .stream()
                .filter(row -> userId.equals(String.valueOf(row.get("user_id"))))
                .limit(PASSAGE_DISPLAY_LIMIT)
                .toList();
    }

    private String formatGraphBlock(List<Map<String, Object>> entities, List<Map<String, Object>> relations,
                                    List<Map<String, Object>> passages) {
        String entityLines = entities.stream()
                .limit(ENTITY_DISPLAY_LIMIT)
                .map(row -> "- " + row.get("text") + "（" + row.get("type") + "）")
                .collect(Collectors.joining("\n"));
        String relationLines = relations.stream()
                .limit(RELATION_DISPLAY_LIMIT)
                .map(row -> "- " + row.get("text"))
                .collect(Collectors.joining("\n"));
        String passageLines = passages.stream()
                .limit(PASSAGE_DISPLAY_LIMIT)
                .map(row -> "- " + compact(row.get("text")))
                .collect(Collectors.joining("\n"));
        return "【图谱关系上下文】\n实体：\n" + entityLines
                + "\n关系：\n" + relationLines
                + (passageLines.isBlank() ? "" : "\n来源片段：\n" + passageLines);
    }

    private void putById(Map<String, Map<String, Object>> rowsById, Map<String, Object> row) {
        Object id = row.get("id");
        if (id != null) {
            rowsById.put(id.toString(), row);
        }
    }

    private List<Map<String, Object>> queryByIds(String collectionName, Collection<String> ids, String userId, List<String> outputFields) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String filter = "(" + MilvusQueryUtil.userFilter(userId) + ") and id in [" + ids.stream()
                .map(id -> "\"" + MilvusQueryUtil.escape(id) + "\"")
                .collect(Collectors.joining(", ")) + "]";
        return MilvusQueryUtil.queryByFilter(milvusClientV2, collectionName, filter, outputFields, ids.size());
    }

    private List<String> parseJsonList(Object value) {
        if (value == null || value.toString().isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value.toString());
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String text = item.asText();
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return values;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String compact(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replaceAll("\\s+", " ").trim();
        return text.length() <= 180 ? text : text.substring(0, 180) + "...";
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float v : vector) {
            values.add(v);
        }
        return values;
    }
}
