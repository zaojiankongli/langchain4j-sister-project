package com.zjkl.memory.config;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 */
@Configuration
public class MilvusEmbeddingStoreConfig {
    
    @Value("${milvus.host:localhost}")
    private String host;
    
    @Value("${milvus.port:19530}")
    private Integer port;
    
    @Value("${milvus.database:default}")
    private String database;
    
    @Value("${milvus.collection-name:zjkl_sister}")
    private String collectionName;
    
    @Bean
    public MilvusEmbeddingStore milvusEmbeddingStore() {
        return MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName(database)
                .collectionName(collectionName)
                .dimension(1024)
                .build();
    }
}
