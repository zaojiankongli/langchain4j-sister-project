package com.zjkl.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.config.properties.MilvusProperties;
import com.zjkl.common.util.MilvusQueryUtil;
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
import java.util.Set;
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

    public GraphQueryService(MilvusClientV2 milvusClientV2,
                             MilvusProperties milvusProperties,
                             EmbeddingModel embeddingModel,
                             QwenChatModel qwenChatModel,
                             PromptTemplateService promptTemplateService,
                             ObjectMapper objectMapper) {
        this.milvusClientV2 = milvusClientV2;
        this.milvusProperties = milvusProperties;
        this.embeddingModel = embeddingModel;
        this.qwenChatModel = qwenChatModel;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
    }

    public GraphResult buildGraphBlock(String userId, String question) {
        if (question == null || question.isBlank()) {
            return GraphResult.empty();
        }

        List<String> queryEntities = extractEntities(question);
        if (queryEntities.isEmpty()) {
            return GraphResult.empty();
        }

        List<Map<String, Object>> matchedEntities = searchEntities(userId, queryEntities);
        // 取 top 实体分数作为图谱检索相关度评分（用于跨路 RAG 融合排序）
        double topScore = matchedEntities.isEmpty() ? 0.0
                : ((Number) matchedEntities.get(0).get("score")).doubleValue();

        Set<String> entityTexts = matchedEntities.stream()
                .map(row -> row.get("text"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (entityTexts.isEmpty()) {
            log.debug("图查询未命中实体 userId={}, question={}", userId, question);
            return GraphResult.empty();
        }

        List<Map<String, Object>> candidateRelations = collectCandidateRelations(userId, question, entityTexts);
        if (candidateRelations.isEmpty()) {
            return new GraphResult(formatEntityOnlyBlock(matchedEntities), topScore);
        }

        List<Map<String, Object>> reranked = rerankRelations(question, candidateRelations);
        log.debug("图查询命中 userId={}, entities={}, candidateRelations={}, reranked={}",
                userId, matchedEntities.size(), candidateRelations.size(), reranked.size());
        List<Map<String, Object>> finalRelations = reranked.isEmpty() ? candidateRelations : reranked;
        return new GraphResult(formatGraphBlock(matchedEntities, finalRelations), topScore);
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
                .outputFields(List.of("id", "text", "type", "mention_count", "last_seen"))
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
                .sorted(Comparator.comparingDouble((Map<String, Object> row) -> ((Number) row.get("score")).doubleValue()).reversed())
                .limit(ENTITY_RESULT_LIMIT)
                .toList();
    }

    private List<Map<String, Object>> collectCandidateRelations(String userId, String question, Set<String> entityTexts) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        String relationFilter = MilvusQueryUtil.userFilter(userId) + " and (" + entityTexts.stream()
                .map(text -> "subject == \"" + MilvusQueryUtil.escape(text) + "\" or object == \"" + MilvusQueryUtil.escape(text) + "\"")
                .collect(Collectors.joining(" or ")) + ")";
        for (Map<String, Object> relation : MilvusQueryUtil.queryByFilter(milvusClientV2,
                milvusProperties.getGraphRelationCollectionName(),
                relationFilter,
                List.of("id", "text", "subject", "predicate", "object", "relation_type", "confidence", "timestamp")
        )) {
            merged.put(relation.get("id").toString(), relation);
        }

        Embedding embedding = embeddingModel.embed(question).content();
        embedding.normalize();
        SearchReq req = SearchReq.builder()
                .collectionName(milvusProperties.getGraphRelationCollectionName())
                .data(List.of(new FloatVec(toFloatList(embedding.vector()))))
                .topK(RELATION_SEARCH_TOP_K)
                .filter(MilvusQueryUtil.userFilter(userId))
                .outputFields(List.of("id", "text", "subject", "predicate", "object", "relation_type", "confidence", "timestamp"))
                .build();
        SearchResp resp = milvusClientV2.search(req);
        if (resp.getSearchResults() != null) {
            for (List<SearchResp.SearchResult> list : resp.getSearchResults()) {
                for (SearchResp.SearchResult item : list) {
                    Map<String, Object> row = new HashMap<>(item.getEntity());
                    row.put("id", item.getId().toString());
                    row.put("score", item.getScore());
                    merged.put(item.getId().toString(), row);
                }
            }
        }

        return new ArrayList<>(merged.values());
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

    private String formatGraphBlock(List<Map<String, Object>> entities, List<Map<String, Object>> relations) {
        String entityLines = entities.stream()
                .limit(ENTITY_DISPLAY_LIMIT)
                .map(row -> "- " + row.get("text") + "（" + row.get("type") + "）")
                .collect(Collectors.joining("\n"));
        String relationLines = relations.stream()
                .limit(RELATION_DISPLAY_LIMIT)
                .map(row -> "- " + row.get("text"))
                .collect(Collectors.joining("\n"));
        return "【图谱关系上下文】\n实体：\n" + entityLines + "\n关系：\n" + relationLines;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float v : vector) {
            values.add(v);
        }
        return values;
    }
}
