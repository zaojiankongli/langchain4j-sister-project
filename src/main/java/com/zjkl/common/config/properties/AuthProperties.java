package com.zjkl.common.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * JWT 认证配置
 * 对应 application.yml 中 app.auth.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** JWT 签名密钥 */
    @NotBlank
    @Size(min = 32, message = "JWT 签名密钥长度不能少于 32 字符")
    private String secret;

    /** Access Token 过期时间（毫秒），默认 2 小时 */
    @Positive
    private Long accessTokenExpiration;

    /** Refresh Token 过期时间（毫秒），默认 7 天 */
    @Positive
    private Long refreshTokenExpiration;

    /** 无需认证的公开路径前缀 */
    private List<String> whitelist;

    /** 管理员用户 ID 列表，用于管理接口权限校验 */
    private List<String> adminIds;

    /**
     * 判断指定用户是否为管理员
     */
    public boolean isAdmin(String userId) {
        return userId != null && adminIds != null && adminIds.contains(userId);
    }

    @AssertTrue(message = "JWT 签名密钥不能使用占位值")
    public boolean isSecretProductionReady() {
        return secret == null || !"change-me-in-production".equals(secret);
    }

}
