package com.zjkl.common.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis 连接配置（用于 Redisson）
 * 对应 application.yml 中 spring.data.redis.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.redis")
public class RedisProperties {

    /** Redis 主机地址 */
    @NotBlank
    private String host;

    /** Redis 端口 */
    @Min(1)
    @Max(65535)
    private int port;

    /** Redis 密码（默认空） */
    private String password = "";

    /** Redis 数据库编号（默认：0） */
    @Min(0)
    private int database = 0;

}
