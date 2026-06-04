package com.zjkl.memory.config;

import com.zjkl.common.config.properties.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class GraphMilvusCollectionManager {

    private static final int GRAPH_VECTOR_DIM = 1024;

    private final MilvusClientV2 client;
    private final MilvusProperties milvusProperties;

    public GraphMilvusCollectionManager(MilvusClientV2 client, MilvusProperties milvusProperties) {
        this.client = client;
        this.milvusProperties = milvusProperties;
    }

    @PostConstruct
    public void init() {
        ensureEntityCollection();
        ensureRelationCollection();
        ensurePassageCollection();
    }

    private void ensureEntityCollection() {
        String collectionName = milvusProperties.getGraphEntityCollectionName();
        if (hasCollection(collectionName)) {
            load(collectionName);
            return;
        }

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(varcharField("id", 256, true));
        schema.addField(varcharField("user_id", 128, false));
        schema.addField(varcharField("text", 1024, false));
        schema.addField(varcharField("type", 64, false));
        schema.addField(int64Field("mention_count"));
        schema.addField(int64Field("first_seen"));
        schema.addField(int64Field("last_seen"));
        schema.addField(vectorField("vector", GRAPH_VECTOR_DIM));

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex("vector")))
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
        load(collectionName);
        log.info("图实体集合已创建: {}", collectionName);
    }

    private void ensureRelationCollection() {
        String collectionName = milvusProperties.getGraphRelationCollectionName();
        if (hasCollection(collectionName)) {
            load(collectionName);
            return;
        }

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(varcharField("id", 256, true));
        schema.addField(varcharField("user_id", 128, false));
        schema.addField(varcharField("text", 2048, false));
        schema.addField(varcharField("subject", 1024, false));
        schema.addField(varcharField("predicate", 256, false));
        schema.addField(varcharField("object", 1024, false));
        schema.addField(varcharField("relation_type", 64, false));
        schema.addField(varcharField("source_id", 256, false));
        schema.addField(floatField("confidence"));
        schema.addField(int64Field("timestamp"));
        schema.addField(vectorField("vector", GRAPH_VECTOR_DIM));

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex("vector")))
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
        load(collectionName);
        log.info("图关系集合已创建: {}", collectionName);
    }

    private void ensurePassageCollection() {
        String collectionName = milvusProperties.getGraphPassageCollectionName();
        if (hasCollection(collectionName)) {
            load(collectionName);
            return;
        }

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(varcharField("id", 256, true));
        schema.addField(varcharField("user_id", 128, false));
        schema.addField(varcharField("text", 65535, false));
        schema.addField(varcharField("source_type", 64, false));

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
        load(collectionName);
        log.info("图段落集合已创建: {}", collectionName);
    }

    private boolean hasCollection(String collectionName) {
        return Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .build()));
    }

    private void load(String collectionName) {
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build());
    }

    private AddFieldReq varcharField(String name, int length, boolean primaryKey) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.VarChar)
                .maxLength(length)
                .isPrimaryKey(primaryKey)
                .autoID(false)
                .build();
    }

    private AddFieldReq vectorField(String name, int dimension) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();
    }

    private AddFieldReq int64Field(String name) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.Int64)
                .build();
    }

    private AddFieldReq floatField(String name) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.Float)
                .build();
    }

    private IndexParam vectorIndex(String fieldName) {
        return IndexParam.builder()
                .fieldName(fieldName)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.IP)
                .build();
    }
}
