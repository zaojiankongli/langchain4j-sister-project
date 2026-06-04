package com.zjkl.memory.config;

import com.zjkl.common.config.properties.MilvusProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 原生客户端配置
 * <p>
 * 替换 langchain4j 的 MilvusEmbeddingStore，支持自定义 schema、BM25 全文检索和混合检索。
 */
@Slf4j
@Configuration
public class MilvusClientConfig {

    private final MilvusProperties milvusProperties;
    private MilvusClientV2 client;

    public MilvusClientConfig(MilvusProperties milvusProperties) {
        this.milvusProperties = milvusProperties;
    }

    @Bean
    public MilvusClientV2 milvusClientV2() {
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusProperties.getUri())
                .token(milvusProperties.getToken())
                .dbName(milvusProperties.getDatabase())
                .connectTimeoutMs(milvusProperties.getConnectTimeoutMs())
                .idleTimeoutMs(milvusProperties.getIdleTimeoutMs())
                .build();
        client = new MilvusClientV2(config);
        log.info("MilvusClientV2 已连接：uri={}, db={}",
                milvusProperties.getUri(), milvusProperties.getDatabase());
        return client;
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
                log.info("MilvusClientV2 已关闭");
            } catch (Exception e) {
                log.warn("MilvusClientV2 关闭异常", e);
            }
        }
    }
}
