package com.zjkl.common.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Milvus 向量数据库配置
 * 对应 application.yml 中 milvus.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.milvus")
public class MilvusProperties {

    /** Milvus 主机地址（默认：localhost） */
    @NotBlank
    private String host = "localhost";

    /** Milvus 端口（默认：19530） */
    @Min(1)
    @Max(65535)
    private Integer port = 19530;

    /** Milvus 数据库名称（默认：default） */
    @NotBlank
    private String database = "default";

    /** Milvus 集合名称（默认：zjkl_sister） */
    @NotBlank
    private String collectionName = "zjkl_sister";

    /** 记忆存储集合名称（混合检索，默认：memory_store） */
    private String memoryCollectionName = "memory_store";

    /** 图实体集合名称 */
    private String graphEntityCollectionName = "vgrag_entities";

    /** 图关系集合名称 */
    private String graphRelationCollectionName = "vgrag_relations";

    /** 图段落集合名称 */
    private String graphPassageCollectionName = "vgrag_passages";

    /** Milvus 连接 URI（默认：http://localhost:19530） */
    @NotBlank
    private String uri = "http://localhost:19530";

    /** Milvus 认证 Token（可选） */
    private String token;

    /** 远程 Milvus 连接超时时间（毫秒） */
    @Min(1000)
    @Max(60000)
    private Long connectTimeoutMs = 5000L;

    /** 远程 Milvus 空闲超时时间（毫秒） */
    @Min(5000)
    @Max(300000)
    private Long idleTimeoutMs = 30000L;

}
