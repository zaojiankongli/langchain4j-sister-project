package com.zjkl.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.prompt.service.PromptTemplateService;
import com.zjkl.common.config.properties.MilvusProperties;
import com.zjkl.common.util.MilvusQueryUtil;
import com.zjkl.emotion.model.EmotionAnchorEvent;
import com.zjkl.memory.constant.GraphRedisKeys;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Service
@Slf4j
public class GraphEntityService {

    private static final String GRAPH_COMPACT_LOCK_KEY_PREFIX = "graph:compact:";
    private static final int ENTITY_LIMIT = 1000;
    private static final int ENTITY_TARGET_AFTER_EVICT = 800;
    private static final int MAX_TRIPLETS = 12;

    private final MilvusClientV2 milvusClientV2;
    private final MilvusProperties milvusProperties;
    private final EmbeddingModel embeddingModel;
    private final QwenChatModel qwenChatModel;
    private final PromptTemplateService promptTemplateService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public GraphEntityService(MilvusClientV2 milvusClientV2,
                              MilvusProperties milvusProperties,
                              EmbeddingModel embeddingModel,
                              QwenChatModel qwenChatModel,
                              PromptTemplateService promptTemplateService,
                              StringRedisTemplate stringRedisTemplate,
                              ObjectMapper objectMapper) {
        this.milvusClientV2 = milvusClientV2;
        this.milvusProperties = milvusProperties;
        this.embeddingModel = embeddingModel;
        this.qwenChatModel = qwenChatModel;
        this.promptTemplateService = promptTemplateService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void ingestAnchorEvent(EmotionAnchorEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            return;
        }
        if (event.getSummary() == null || event.getSummary().isBlank()) {
            log.debug("图写入跳过：summary 为空 userId={}", event.getUserId());
            return;
        }

        String userId = event.getUserId();
        String content = buildGraphSourceContent(event);
        String contentHash = md5(content);

        if (isDuplicate(userId, contentHash)) {
            log.debug("图写入跳过：内容 hash 未变化 userId={}", userId);
            return;
        }
        if (isRapidFireBlocked(userId)) {
            log.debug("图写入跳过：rapid fire 限制 userId={}", userId);
            return;
        }

        List<TripletRecord> triplets = extractTriplets(content);
        if (triplets.isEmpty()) {
            stringRedisTemplate.opsForValue().set(GraphRedisKeys.LAST_HASH_KEY + userId,
                    contentHash, GraphRedisKeys.HASH_TTL);
            log.debug("图写入跳过：未抽到三元组 userId={}", userId);
            return;
        }

        upsertGraph(userId, event, triplets);
        stringRedisTemplate.opsForValue().set(GraphRedisKeys.LAST_HASH_KEY + userId,
                contentHash, GraphRedisKeys.HASH_TTL);
        stringRedisTemplate.opsForSet().add(GraphRedisKeys.KNOWN_USERS_KEY, userId);
        stringRedisTemplate.opsForValue().set(GraphRedisKeys.LAST_WRITE_BATCH_KEY + userId,
                String.valueOf(System.currentTimeMillis()), GraphRedisKeys.SNAPSHOT_TTL);

        evictEntitiesIfNeeded(userId);
        log.info("图写入完成 userId={}, triplets={}", userId, triplets.size());
    }

    @Scheduled(cron = "0 0 3 * * SUN")
    public void weeklyCompactGraph() {
        // 先 flush 确保所有未密封数据落盘
        flushGraphCollections();

        Set<String> userIds = stringRedisTemplate.opsForSet().members(GraphRedisKeys.KNOWN_USERS_KEY);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds) {
            String lockKey = GRAPH_COMPACT_LOCK_KEY_PREFIX + userId;
            boolean acquired = false;
            try {
                Boolean setIfAbsent = stringRedisTemplate.opsForValue().setIfAbsent(
                        lockKey,
                        "1",
                        Duration.ofHours(6)
                );
                acquired = Boolean.TRUE.equals(setIfAbsent);
                if (!acquired) {
                    continue;
                }

                mergeNearDuplicateEntities(userId);
                evictEntitiesIfNeeded(userId);
            } catch (Exception e) {
                if (acquired) {
                    stringRedisTemplate.delete(lockKey);
                }
                log.warn("图压缩失败 userId={}", userId, e);
            }
        }
    }

    private void upsertGraph(String userId, EmotionAnchorEvent event, List<TripletRecord> triplets) {
        String sourceId = buildSourceId(userId, event);
        upsertPassage(userId, sourceId, buildGraphSourceContent(event));

        Map<String, String> entityTypeMap = new LinkedHashMap<>();
        for (TripletRecord triplet : triplets) {
            entityTypeMap.putIfAbsent(MilvusQueryUtil.normalizePhrase(triplet.subject()), inferType(triplet.subject(), triplet.type()));
            entityTypeMap.putIfAbsent(MilvusQueryUtil.normalizePhrase(triplet.object()), inferType(triplet.object(), triplet.type()));
        }

        upsertEntities(userId, sourceId, entityTypeMap);
        upsertRelations(userId, sourceId, triplets);
        // 不在每次写入时 flush，依赖 Milvus 自动 flush 和每周 compaction 任务
    }

    private void upsertPassage(String userId, String sourceId, String text) {
        JsonObject row = new JsonObject();
        row.addProperty("id", sourceId);
        row.addProperty("user_id", userId);
        row.addProperty("text", text);
        row.addProperty("source_type", "anchor");
        milvusClientV2.upsert(UpsertReq.builder()
                .collectionName(milvusProperties.getGraphPassageCollectionName())
                .data(List.of(row))
                .build());
    }

    private void upsertEntities(String userId, String sourceId, Map<String, String> entityTypeMap) {
        if (entityTypeMap.isEmpty()) {
            return;
        }
        List<String> entityIds = entityTypeMap.keySet().stream().map(name -> entityId(userId, name)).toList();
        Map<String, Map<String, Object>> existing = queryByIds(
                milvusProperties.getGraphEntityCollectionName(),
                entityIds,
                List.of("id", "mention_count", "first_seen", "last_seen", "type", "source_ids")
        );

        // 批量 embedding，避免 N+1 API 调用
        List<String> names = new ArrayList<>(entityTypeMap.keySet());
        List<dev.langchain4j.data.segment.TextSegment> segments = names.stream()
                .map(dev.langchain4j.data.segment.TextSegment::from)
                .toList();
        List<Embedding> batchEmbeddings = embeddingModel.embedAll(segments).content();

        List<JsonObject> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int idx = 0; idx < names.size() && idx < batchEmbeddings.size(); idx++) {
            String name = names.get(idx);
            String id = entityId(userId, name);
            Map<String, Object> current = existing.get(id);
            long mentionCount = current == null ? 1L : parseLong(current.get("mention_count")) + 1;
            long firstSeen = current == null ? now : parseLong(current.get("first_seen"));
            String type = current != null && current.get("type") != null ? current.get("type").toString() : entityTypeMap.get(name);
            String sourceIds = appendJsonList(current == null ? null : current.get("source_ids"), sourceId);

            Embedding embedding = batchEmbeddings.get(idx);
            normalizeEmbedding(embedding);

            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            row.addProperty("user_id", userId);
            row.addProperty("text", name);
            row.addProperty("type", type);
            row.addProperty("mention_count", mentionCount);
            row.addProperty("first_seen", firstSeen);
            row.addProperty("last_seen", now);
            row.addProperty("source_ids", sourceIds);
            row.add("vector", toJsonArray(embedding.vector()));
            rows.add(row);
        }

        milvusClientV2.upsert(UpsertReq.builder()
                .collectionName(milvusProperties.getGraphEntityCollectionName())
                .data(rows)
                .build());
    }

    private void upsertRelations(String userId, String sourceId, List<TripletRecord> triplets) {
        Map<String, Map<String, Object>> existing = queryByIds(
                milvusProperties.getGraphRelationCollectionName(),
                triplets.stream().map(t -> relationId(userId, t.subject(), t.predicate(), t.object())).toList(),
                List.of("id", "confidence", "timestamp")
        );

        // 批量 embedding
        List<String> relationTexts = triplets.stream()
                .map(t -> t.subject() + " " + t.predicate() + " " + t.object())
                .toList();
        List<dev.langchain4j.data.segment.TextSegment> relSegments = relationTexts.stream()
                .map(dev.langchain4j.data.segment.TextSegment::from)
                .toList();
        List<Embedding> relEmbeddings = embeddingModel.embedAll(relSegments).content();

        List<JsonObject> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int idx = 0; idx < triplets.size() && idx < relEmbeddings.size(); idx++) {
            TripletRecord triplet = triplets.get(idx);
            String id = relationId(userId, triplet.subject(), triplet.predicate(), triplet.object());
            Map<String, Object> current = existing.get(id);
            float confidence = current == null
                    ? triplet.confidence()
                    : Math.max((float) parseDouble(current.get("confidence")), triplet.confidence());
            String text = relationTexts.get(idx);

            Embedding embedding = relEmbeddings.get(idx);
            normalizeEmbedding(embedding);

            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            row.addProperty("user_id", userId);
            row.addProperty("text", text);
            row.addProperty("subject", triplet.subject());
            row.addProperty("predicate", triplet.predicate());
            row.addProperty("object", triplet.object());
            row.addProperty("relation_type", triplet.type());
            row.addProperty("confidence", confidence);
            row.addProperty("timestamp", current == null ? now : parseLong(current.get("timestamp")));
            row.addProperty("source_id", sourceId);
            row.add("vector", toJsonArray(embedding.vector()));
            rows.add(row);
        }

        milvusClientV2.upsert(UpsertReq.builder()
                .collectionName(milvusProperties.getGraphRelationCollectionName())
                .data(rows)
                .build());
    }

    private void evictEntitiesIfNeeded(String userId) {
        // 查询上限必须大于 ENTITY_LIMIT，否则 rows.size() 永远 <= 500 < 1000，驱逐永不触发
        List<Map<String, Object>> rows = MilvusQueryUtil.queryByFilter(milvusClientV2,
                milvusProperties.getGraphEntityCollectionName(),
                MilvusQueryUtil.userFilter(userId),
                List.of("id", "text", "type", "mention_count", "last_seen"),
                ENTITY_LIMIT + 1
        );
        if (rows.size() <= ENTITY_LIMIT) {
            return;
        }

        List<Map<String, Object>> candidates = rows.stream()
                .filter(row -> !"person".equals(row.get("type")))
                .filter(row -> parseLong(row.get("mention_count")) < 5)
                .sorted(Comparator.comparingLong(row -> parseLong(row.get("last_seen"))))
                .toList();

        int toDeleteCount = Math.max(0, rows.size() - ENTITY_TARGET_AFTER_EVICT);
        if (toDeleteCount == 0 || candidates.isEmpty()) {
            return;
        }
        List<Map<String, Object>> toDelete = candidates.stream().limit(toDeleteCount).toList();
        deleteEntitiesAndOrphans(userId, toDelete);
        log.info("图 LRU 淘汰完成 userId={}, deletedEntities={}", userId, toDelete.size());
    }

    private void mergeNearDuplicateEntities(String userId) {
        // 需要查询全部实体才能做全局去重比较，上限设为 ENTITY_LIMIT * 2
        List<Map<String, Object>> rows = MilvusQueryUtil.queryByFilter(milvusClientV2,
                milvusProperties.getGraphEntityCollectionName(),
                MilvusQueryUtil.userFilter(userId),
                List.of("id", "text", "type", "mention_count", "first_seen", "last_seen", "source_ids"),
                ENTITY_LIMIT * 2
        );
        Set<String> deletedIds = new HashSet<>();

        // 按类型分组，只在同类型实体间比较，避免 O(n²) 全量对比
        Map<String, List<Map<String, Object>>> byType = rows.stream()
                .collect(Collectors.groupingBy(row -> row.get("type") != null ? row.get("type").toString() : ""));

        for (List<Map<String, Object>> group : byType.values()) {
            for (int i = 0; i < group.size(); i++) {
                Map<String, Object> left = group.get(i);
                if (deletedIds.contains(left.get("id").toString())) {
                    continue;
                }
                for (int j = i + 1; j < group.size(); j++) {
                    Map<String, Object> right = group.get(j);
                    if (deletedIds.contains(right.get("id").toString())) {
                        continue;
                    }
                    String leftText = left.get("text").toString();
                    String rightText = right.get("text").toString();
                    if (editDistance(leftText, rightText) >= 3) {
                        continue;
                    }
                    Map<String, Object> keep = parseLong(left.get("mention_count")) >= parseLong(right.get("mention_count")) ? left : right;
                    Map<String, Object> remove = keep == left ? right : left;
                    mergeEntityInto(userId, keep, remove);
                    deletedIds.add(remove.get("id").toString());
                }
            }
        }
    }

    private void mergeEntityInto(String userId, Map<String, Object> keep, Map<String, Object> remove) {
        String keepText = keep.get("text").toString();
        String removeText = remove.get("text").toString();

        JsonObject keepRow = new JsonObject();
        keepRow.addProperty("id", keep.get("id").toString());
        keepRow.addProperty("user_id", userId);
        keepRow.addProperty("text", keepText);
        keepRow.addProperty("type", keep.get("type").toString());
        keepRow.addProperty("mention_count", parseLong(keep.get("mention_count")) + parseLong(remove.get("mention_count")));
        keepRow.addProperty("first_seen", Math.min(parseLong(keep.get("first_seen")), parseLong(remove.get("first_seen"))));
        keepRow.addProperty("last_seen", Math.max(parseLong(keep.get("last_seen")), parseLong(remove.get("last_seen"))));
        keepRow.addProperty("source_ids", mergeJsonLists(keep.get("source_ids"), remove.get("source_ids")));
        Embedding embedding = embeddingModel.embed(keepText).content();
        normalizeEmbedding(embedding);
        keepRow.add("vector", toJsonArray(embedding.vector()));
        milvusClientV2.upsert(UpsertReq.builder()
                .collectionName(milvusProperties.getGraphEntityCollectionName())
                .data(List.of(keepRow))
                .build());

        rewriteRelations(userId, keepText, removeText);
        milvusClientV2.delete(DeleteReq.builder()
                .collectionName(milvusProperties.getGraphEntityCollectionName())
                .filter("id == \"" + remove.get("id") + "\"")
                .build());
    }

    private void rewriteRelations(String userId, String keepText, String removeText) {
        List<Map<String, Object>> relations = MilvusQueryUtil.queryByFilter(milvusClientV2,
                milvusProperties.getGraphRelationCollectionName(),
                MilvusQueryUtil.userFilter(userId) + " and (subject == \"" + MilvusQueryUtil.escape(removeText) + "\" or object == \"" + MilvusQueryUtil.escape(removeText) + "\")",
                List.of("id", "subject", "predicate", "object", "relation_type", "confidence", "timestamp", "source_id")
        );
        if (relations.isEmpty()) {
            return;
        }

        // 先批量构建所有新文本，再一次性 embedAll，避免 N+1 embedding 调用
        List<String> newTexts = new ArrayList<>();
        List<String> newIds = new ArrayList<>();
        List<String> newSubjects = new ArrayList<>();
        List<String> newObjects = new ArrayList<>();
        List<String> deletes = new ArrayList<>();

        for (Map<String, Object> relation : relations) {
            String subject = relation.get("subject").toString();
            String object = relation.get("object").toString();
            String newSubject = subject.equals(removeText) ? keepText : subject;
            String newObject = object.equals(removeText) ? keepText : object;
            newSubjects.add(newSubject);
            newObjects.add(newObject);
            newIds.add(relationId(userId, newSubject, relation.get("predicate").toString(), newObject));
            newTexts.add(newSubject + " " + relation.get("predicate") + " " + newObject);
            deletes.add(relation.get("id").toString());
        }

        // 批量 embedding
        List<dev.langchain4j.data.segment.TextSegment> segments = newTexts.stream()
                .map(dev.langchain4j.data.segment.TextSegment::from)
                .toList();
        List<Embedding> batchEmbeddings = embeddingModel.embedAll(segments).content();

        List<JsonObject> upserts = new ArrayList<>();
        for (int i = 0; i < relations.size(); i++) {
            Map<String, Object> relation = relations.get(i);
            Embedding embedding = batchEmbeddings.get(i);
            normalizeEmbedding(embedding);

            JsonObject row = new JsonObject();
            row.addProperty("id", newIds.get(i));
            row.addProperty("user_id", userId);
            row.addProperty("text", newTexts.get(i));
            row.addProperty("subject", newSubjects.get(i));
            row.addProperty("predicate", relation.get("predicate").toString());
            row.addProperty("object", newObjects.get(i));
            row.addProperty("relation_type", relation.get("relation_type").toString());
            row.addProperty("confidence", parseDouble(relation.get("confidence")));
            row.addProperty("timestamp", parseLong(relation.get("timestamp")));
            row.addProperty("source_id", relation.get("source_id").toString());
            row.add("vector", toJsonArray(embedding.vector()));
            upserts.add(row);
        }
        milvusClientV2.upsert(UpsertReq.builder()
                .collectionName(milvusProperties.getGraphRelationCollectionName())
                .data(upserts)
                .build());
        deleteByIds(milvusProperties.getGraphRelationCollectionName(), deletes);
    }

    private void deleteEntitiesAndOrphans(String userId, List<Map<String, Object>> entities) {
        if (entities.isEmpty()) {
            return;
        }
        List<String> entityIds = entities.stream().map(row -> row.get("id").toString()).toList();
        deleteByIds(milvusProperties.getGraphEntityCollectionName(), entityIds);

        Set<String> names = entities.stream().map(row -> row.get("text").toString()).collect(Collectors.toSet());
        String relationFilter = MilvusQueryUtil.userFilter(userId) + " and (" + names.stream()
                .map(name -> "subject == \"" + MilvusQueryUtil.escape(name) + "\" or object == \"" + MilvusQueryUtil.escape(name) + "\"")
                .collect(Collectors.joining(" or ")) + ")";
        List<Map<String, Object>> orphans = MilvusQueryUtil.queryByFilter(milvusClientV2,
                milvusProperties.getGraphRelationCollectionName(),
                relationFilter,
                List.of("id")
        );
        deleteByIds(milvusProperties.getGraphRelationCollectionName(),
                orphans.stream().map(row -> row.get("id").toString()).toList());
    }

    private void flushGraphCollections() {
        milvusClientV2.flush(FlushReq.builder()
                .databaseName(milvusProperties.getDatabase())
                .collectionNames(List.of(
                        milvusProperties.getGraphEntityCollectionName(),
                        milvusProperties.getGraphRelationCollectionName(),
                        milvusProperties.getGraphPassageCollectionName()
                ))
                .build());
    }

    private List<TripletRecord> extractTriplets(String content) {
        String prompt = promptTemplateService.render("graph-triplets", Map.of("content", content));
        try {
            ChatResponse response = qwenChatModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from("你是一个图谱抽取器，只输出 JSON。"),
                            UserMessage.from(prompt)
                    )
                    .build());
            String text = response.aiMessage() != null ? response.aiMessage().text() : null;
            if (text == null || text.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(MilvusQueryUtil.extractJson(text));
            JsonNode tripletsNode = root.path("triplets");
            if (!tripletsNode.isArray()) {
                return List.of();
            }
            List<TripletRecord> result = new ArrayList<>();
            for (JsonNode node : tripletsNode) {
                String subject = MilvusQueryUtil.normalizePhrase(node.path("subject").asText());
                String predicate = MilvusQueryUtil.normalizePhrase(node.path("predicate").asText());
                String object = MilvusQueryUtil.normalizePhrase(node.path("object").asText());
                String type = node.path("type").asText("general");
                float confidence = (float) node.path("confidence").asDouble(0.8D);
                if (!subject.isBlank() && !predicate.isBlank() && !object.isBlank()) {
                    result.add(new TripletRecord(subject, predicate, object, type, confidence));
                }
                if (result.size() >= MAX_TRIPLETS) {
                    break;
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("图三元组抽取失败", e);
            return List.of();
        }
    }

    private String buildGraphSourceContent(EmotionAnchorEvent event) {
        return String.join("\n",
                nonNullLine("事件标题", event.getEventTitle()),
                nonNullLine("触发原因", event.getTriggerReason()),
                nonNullLine("重点特征", event.getHighlightTraits()),
                nonNullLine("事件摘要", event.getSummary()),
                nonNullLine("结束原因", event.getEndReason()),
                nonNullLine("AI反思", event.getAiReflection())
        );
    }

    private String nonNullLine(String key, String value) {
        return key + "：" + (value == null ? "" : value);
    }

    private boolean isDuplicate(String userId, String contentHash) {
        String oldHash = stringRedisTemplate.opsForValue().get(GraphRedisKeys.LAST_HASH_KEY + userId);
        return Objects.equals(oldHash, contentHash);
    }

    private boolean isRapidFireBlocked(String userId) {
        String key = GraphRedisKeys.RAPID_FIRE_KEY + userId;
        // Check if currently in block window
        String value = stringRedisTemplate.opsForValue().get(key);
        if ("blocked".equals(value)) {
            return true;
        }
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (Long.valueOf(1L).equals(count)) {
            stringRedisTemplate.expire(key, GraphRedisKeys.RAPID_FIRE_WINDOW);
        }
        if (count != null && count >= 3L) {
            // Set block marker with block TTL; counter resets when TTL expires
            stringRedisTemplate.opsForValue().set(key, "blocked", GraphRedisKeys.RAPID_FIRE_BLOCK);
            return true;
        }
        return false;
    }

    private Map<String, Map<String, Object>> queryByIds(String collectionName, List<String> ids, List<String> outputFields) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        String filter = "id in [" + ids.stream().map(id -> "\"" + MilvusQueryUtil.escape(id) + "\"").collect(Collectors.joining(", ")) + "]";
        List<Map<String, Object>> rows = MilvusQueryUtil.queryByFilter(milvusClientV2,collectionName, filter, outputFields);
        return rows.stream().collect(Collectors.toMap(row -> row.get("id").toString(), row -> row, (a, b) -> a, LinkedHashMap::new));
    }

    private void deleteByIds(String collectionName, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String filter = "id in [" + ids.stream().map(id -> "\"" + MilvusQueryUtil.escape(id) + "\"").collect(Collectors.joining(", ")) + "]";
        milvusClientV2.delete(DeleteReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .build());
    }

    private void normalizeEmbedding(Embedding embedding) {
        if (embedding != null) {
            embedding.normalize();
        }
    }

    private JsonArray toJsonArray(float[] values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    private String entityId(String userId, String entityName) {
        return userId + ":" + entityName;
    }

    private String relationId(String userId, String subject, String predicate, String object) {
        return md5(userId + ":" + subject + ":" + predicate + ":" + object);
    }

    private String buildSourceId(String userId, EmotionAnchorEvent event) {
        if (event.getId() != null) {
            return userId + ":anchor:" + event.getId();
        }
        return userId + ":anchor:" + md5(buildGraphSourceContent(event));
    }

    private String appendJsonList(Object currentValue, String value) {
        return mergeJsonLists(currentValue, value == null ? null : "[\"" + MilvusQueryUtil.escape(value) + "\"]");
    }

    private String mergeJsonLists(Object left, Object right) {
        Set<String> values = new LinkedHashSet<>();
        values.addAll(parseJsonList(left));
        values.addAll(parseJsonList(right));
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseJsonList(Object value) {
        if (value == null) {
            return List.of();
        }
        String text = value.toString();
        if (text.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String v = item.asText();
                if (!v.isBlank()) {
                    values.add(v);
                }
            }
            return values;
        } catch (Exception e) {
            return List.of();
        }
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private double parseDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private String inferType(String entity, String relationType) {
        if (relationType != null && relationType.contains("emotion")) {
            return "emotion";
        }
        if (relationType != null && relationType.contains("person")) {
            return "person";
        }
        return "concept";
    }

    private int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("md5 failed", e);
        }
    }

    private record TripletRecord(String subject, String predicate, String object, String type, float confidence) {
    }
}
