package com.zjkl.vectorgraphrag.graph;

import com.zjkl.vectorgraphrag.config.VectorGraphRagSettings;
import com.zjkl.vectorgraphrag.model.*;
import com.zjkl.vectorgraphrag.storage.EmbeddingClient;
import com.zjkl.vectorgraphrag.storage.MilvusStore;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.zjkl.vectorgraphrag.graph.GraphBuilder.normalizePhrase;

/**
 * User-facing graph operations interface.
 * Encapsulates MilvusStore with CRUD operations and automatic entity/relation linking.
 */
@Slf4j
public class Graph {

    private final VectorGraphRagSettings settings;
    private final EmbeddingClient embeddingClient;
    private final MilvusStore store;

    private final Map<String, String> entityNameToId = new ConcurrentHashMap<>();
    private final Map<String, String> relationTextToId = new ConcurrentHashMap<>();

    public Graph(VectorGraphRagSettings settings, MilvusStore store, EmbeddingClient embeddingClient) {
        this.settings = settings;
        this.store = store;
        this.embeddingClient = embeddingClient;
    }

    // ==================== Passage CRUD ====================

    public String createPassage(String text) {
        return createPassage(text, null, null);
    }

    public String createPassage(String text, String id) {
        return createPassage(text, id, null);
    }

    public String createPassage(String text, String id, List<Triplet> triplets) {
        String passageId = id != null ? id : UUID.randomUUID().toString();
        List<Float> embedding = embeddingClient.embed(text);

        Set<String> entityIdSet = new LinkedHashSet<>();
        List<String> relationIds = new ArrayList<>();

        if (triplets != null) {
            for (Triplet triplet : triplets) {
                String relationId = createRelation(triplet.getSubject(), triplet.getPredicate(),
                        triplet.getObject(), null, List.of(passageId));
                relationIds.add(relationId);

                String subjectId = entityNameToId.get(normalizePhrase(triplet.getSubject()));
                String objectId = entityNameToId.get(normalizePhrase(triplet.getObject()));
                if (subjectId != null) entityIdSet.add(subjectId);
                if (objectId != null) entityIdSet.add(objectId);
            }
        }

        List<String> entityIds = new ArrayList<>(entityIdSet);

        Map<String, Object> metadata = new HashMap<>();
        if (!entityIds.isEmpty()) metadata.put("entity_ids", entityIds);
        if (!relationIds.isEmpty()) metadata.put("relation_ids", relationIds);

        List<Map<String, Object>> metadatas = metadata.isEmpty() ? null : List.of(metadata);
        store.insertPassages(List.of(text), List.of(passageId), List.of(embedding), metadatas, false);

        return passageId;
    }

    public Passage getPassage(String passageId) {
        List<Map<String, Object>> results = store.getPassagesByIds(List.of(passageId));
        if (results.isEmpty()) return null;

        Map<String, Object> data = results.get(0);
        return Passage.builder()
                .id(safeGet(data, "id"))
                .text(safeGet(data, "text"))
                .entityIds(safeGetList(data, "entity_ids"))
                .relationIds(safeGetList(data, "relation_ids"))
                .build();
    }

    public List<Passage> searchPassages(String query, int topK) {
        List<Float> queryEmbedding = embeddingClient.embed(query);
        List<Map<String, Object>> results = store.searchPassages(queryEmbedding, topK, null);

        return results.stream().map(r -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) r.get("entity");
            return Passage.builder()
                    .id(safeGet(entity, "id"))
                    .text(safeGet(entity, "text"))
                    .entityIds(safeGetList(entity, "entity_ids"))
                    .relationIds(safeGetList(entity, "relation_ids"))
                    .build();
        }).collect(Collectors.toList());
    }

    public boolean updatePassage(String passageId, String text, List<String> entityIds, List<String> relationIds) {
        return store.upsertPassage(passageId, text, null, entityIds, relationIds);
    }

    public boolean deletePassage(String passageId) {
        List<Map<String, Object>> passages = store.getPassagesByIds(List.of(passageId));
        if (passages.isEmpty()) return false;

        Map<String, Object> data = passages.get(0);
        List<String> entityIds = safeGetList(data, "entity_ids");
        List<String> relationIds = safeGetList(data, "relation_ids");

        // Cascade: remove passage reference from entities (batch fetch)
        if (!entityIds.isEmpty()) {
            List<Map<String, Object>> allEntities = store.getEntitiesByIds(entityIds);
            Map<String, Map<String, Object>> entityMap = new HashMap<>();
            for (Map<String, Object> ent : allEntities) {
                String eid = safeGet(ent, "id");
                if (!eid.isEmpty()) entityMap.put(eid, ent);
            }
            for (Map.Entry<String, Map<String, Object>> entry : entityMap.entrySet()) {
                List<String> pids = safeGetList(entry.getValue(), "passage_ids");
                pids.remove(passageId);
                String existingText = safeGet(entry.getValue(), "text");
                store.upsertEntity(entry.getKey(), existingText, null, null, pids);
            }
        }

        // Cascade: remove passage reference from relations (batch fetch)
        if (!relationIds.isEmpty()) {
            List<Map<String, Object>> allRelations = store.getRelationsByIds(relationIds);
            Map<String, Map<String, Object>> relationMap = new HashMap<>();
            for (Map<String, Object> rel : allRelations) {
                String rid = safeGet(rel, "id");
                if (!rid.isEmpty()) relationMap.put(rid, rel);
            }
            for (Map.Entry<String, Map<String, Object>> entry : relationMap.entrySet()) {
                List<String> pids = safeGetList(entry.getValue(), "passage_ids");
                pids.remove(passageId);
                String existingText = safeGet(entry.getValue(), "text");
                store.upsertRelation(entry.getKey(), existingText, null, null, pids, null, null, null);
            }
        }

        return store.deletePassage(passageId);
    }

    // ==================== Private: Entity & Relation CRUD ====================

    private String createRelation(String subject, String predicate, String object,
                                  String id, List<String> passageIds) {
        String normSubject = normalizePhrase(subject);
        String normPredicate = normalizePhrase(predicate);
        String normObject = normalizePhrase(object);
        String relationText = normSubject + " " + normPredicate + " " + normObject;

        return relationTextToId.computeIfAbsent(relationText, key -> {
            // 仅在 Map 中不存在时执行，保证同一 relationText 只有一个线程执行插入
            String relationId = id != null ? id : UUID.randomUUID().toString();

            String subjectId = createEntity(subject, List.of(relationId), passageIds);
            String objectId = createEntity(object, List.of(relationId), passageIds);

            List<Float> embedding = embeddingClient.embed(relationText);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("entity_ids", List.of(subjectId, objectId));
            metadata.put("subject", normSubject);
            metadata.put("predicate", normPredicate);
            metadata.put("object", normObject);
            if (passageIds != null && !passageIds.isEmpty()) {
                metadata.put("passage_ids", passageIds);
            }

            store.insertRelations(List.of(relationText), List.of(relationId),
                    List.of(embedding), List.of(metadata), false);

            return relationId;
        });
    }

    private String createEntity(String name, List<String> relationIds, List<String> passageIds) {
        String normalized = normalizePhrase(name);

        return entityNameToId.computeIfAbsent(normalized, key -> {
            // 仅在 Map 中不存在时执行，保证同一 entity name 只有一个线程执行插入
            String entityId = UUID.randomUUID().toString();
            List<Float> embedding = embeddingClient.embed(normalized);

            Map<String, Object> metadata = new HashMap<>();
            if (relationIds != null && !relationIds.isEmpty()) metadata.put("relation_ids", relationIds);
            if (passageIds != null && !passageIds.isEmpty()) metadata.put("passage_ids", passageIds);

            List<Map<String, Object>> metadatas = metadata.isEmpty() ? null : List.of(metadata);
            store.insertEntities(List.of(normalized), List.of(entityId),
                    List.of(embedding), metadatas, false);

            return entityId;
        });
    }

    // ==================== SubGraph Creation ====================

    public SubGraph createSubGraph() {
        return new SubGraph(store);
    }

    // ==================== Collection Management ====================

    public void createCollections(boolean dropExisting) {
        store.createCollections(dropExisting);
    }

    public void dropCollections() {
        store.dropCollections();
        entityNameToId.clear();
        relationTextToId.clear();
    }

    public void reset() {
        dropCollections();
        store.createCollections(true);
    }

    // ==================== Helpers ====================

    @SuppressWarnings("unchecked")
    private List<String> safeGetList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List) {
            return ((List<Object>) val).stream().map(Object::toString).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String safeGet(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
