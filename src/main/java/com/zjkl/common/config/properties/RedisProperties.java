package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 连接配置（用于 Redisson）
 * 对应 application.yml 中 spring.data.redis.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.redis")
public class RedisProperties {

    /** Redis 主机地址 */
    private String host;

    /** Redis 端口 */
    private int port;

    /** Redis 密码（默认空） */
    private String password = "";

    /** Redis 数据库编号（默认：0） */
    private int database = 0;

}
