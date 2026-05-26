package com.zjkl.memory.config;

import com.zjkl.common.config.properties.MilvusProperties;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 */
@Configuration
@RequiredArgsConstructor
public class MilvusEmbeddingStoreConfig {

    private final MilvusProperties milvusProperties;
    
    @Bean
    public MilvusEmbeddingStore milvusEmbeddingStore() {
        return MilvusEmbeddingStore.builder()
                .host(milvusProperties.getHost())
                .port(milvusProperties.getPort())
                .databaseName(milvusProperties.getDatabase())
                .collectionName(milvusProperties.getCollectionName())
                .dimension(1024)
                .build();
    }
}
