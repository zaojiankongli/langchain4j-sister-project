package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 认证配置
 * 对应 application.yml 中 jwt.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** JWT 签名密钥 */
    private String secret;

    /** Access Token 过期时间（毫秒），默认 2 小时 */
    private Long accessTokenExpiration;

    /** Refresh Token 过期时间（毫秒），默认 7 天 */
    private Long refreshTokenExpiration;

}
