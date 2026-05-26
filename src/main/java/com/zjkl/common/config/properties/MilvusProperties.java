package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 向量数据库配置
 * 对应 application.yml 中 milvus.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.milvus")
public class MilvusProperties {

    /** Milvus 主机地址（默认：localhost） */
    private String host = "localhost";

    /** Milvus 端口（默认：19530） */
    private Integer port = 19530;

    /** Milvus 数据库名称（默认：default） */
    private String database = "default";

    /** Milvus 集合名称（默认：zjkl_sister） */
    private String collectionName = "zjkl_sister";

}
