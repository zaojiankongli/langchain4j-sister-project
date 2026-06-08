package com.zjkl.memory.config;

import com.zjkl.common.config.properties.MilvusProperties;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Milvus 记忆集合初始化
 * <p>
 * 在应用启动时检查并创建 memory_store 集合，配置混合检索所需的 schema 和索引。
 * 如果集合已存在则跳过创建，只执行 load 操作。
 */
@Slf4j
@Configuration
public class MilvusCollectionManager {

    private static final String DENSE_FIELD = "dense_vector";
    private static final String SPARSE_FIELD = "sparse_vector";
    private static final int DENSE_DIM = 1024;

    private final MilvusClientV2 client;
    private final String collectionName;

    /** 集合初始化是否成功（volatile 保证可见性） */
    private volatile boolean collectionReady = false;

    public MilvusCollectionManager(MilvusClientV2 client, MilvusProperties milvusProperties) {
        this.client = client;
        this.collectionName = milvusProperties.getMemoryCollectionName();
    }

    @PostConstruct
    public void init() {
        try {
            if (hasCollection()) {
                log.info("记忆集合 {} 已存在，加载到内存", collectionName);
                loadCollection();
                collectionReady = true;
                return;
            }

            createCollection();
            log.info("记忆集合 {} 创建完成（dense=1024 维 + BM25 全文检索）", collectionName);
            collectionReady = true;
        } catch (Exception e) {
            log.error("!!! Milvus 集合初始化失败，向量检索将不可用: collection={}, error={}",
                    collectionName, e.getMessage(), e);
        }
    }

    /** 检查集合是否已就绪 */
    public boolean isCollectionReady() {
        return collectionReady;
    }

    private boolean hasCollection() {
        return Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .build()));
    }

    private void loadCollection() {
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build());
    }

    private void createCollection() {
        // 1. Schema 定义
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();

        schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .isPrimaryKey(Boolean.TRUE)
                .autoID(Boolean.FALSE)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .enableAnalyzer(Boolean.TRUE)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("title")
                .dataType(DataType.VarChar)
                .maxLength(512)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("user_id")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("create_time")
                .dataType(DataType.VarChar)
                .maxLength(32)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("emotion_label")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("sentiment_score")
                .dataType(DataType.Float)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(DENSE_FIELD)
                .dataType(DataType.FloatVector)
                .dimension(DENSE_DIM)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName(SPARSE_FIELD)
                .dataType(DataType.SparseFloatVector)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("metadata")
                .dataType(DataType.VarChar)
                .maxLength(4096)
                .build());

        // 2. BM25 函数：content → sparse_vector
        schema.addFunction(CreateCollectionReq.Function.builder()
                .functionType(FunctionType.BM25)
                .name("bm25_func")
                .inputFieldNames(Collections.singletonList("content"))
                .outputFieldNames(Collections.singletonList(SPARSE_FIELD))
                .build());

        // 3. 索引
        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(IndexParam.builder()
                .fieldName(DENSE_FIELD)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.IP)
                .build());
        indexParams.add(IndexParam.builder()
                .fieldName(SPARSE_FIELD)
                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25)
                .build());

        // 4. 创建
        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();
        client.createCollection(createReq);

        // 5. 加载
        loadCollection();
    }
}
