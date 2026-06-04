package com.zjkl.common.util;

import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final AuthProperties authProperties;

    private volatile SecretKey cachedSigningKey;

    public Long getAccessTokenExpiration() {
        return authProperties.getAccessTokenExpiration();
    }

    public String generateAccessToken(User user) {
        return createToken(user.getId(), user.getEmail(), user.getUsername(), authProperties.getAccessTokenExpiration());
    }

    /**
     * 根据用户信息直接生成 Access Token（无需查询 DB）
     */
    public String generateAccessToken(String userId, String email, String username) {
        return createToken(userId, email, username, authProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(User user) {
        return createToken(user.getId(), user.getEmail(), user.getUsername(), authProperties.getRefreshTokenExpiration());
    }

    public String parseAccessToken(String token) {
        return parseTokenSubject(token);
    }

    /**
     * 解析 Access Token 中的完整用户信息（userId, email, username）
     *
     * @return 包含 userId/email/username 的 Map，解析失败返回 null
     */
    public Map<String, String> parseAccessTokenClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Map<String, String> result = new java.util.HashMap<>();
            result.put("userId", claims.getSubject());
            result.put("email", claims.get("email", String.class));
            result.put("username", claims.get("username", String.class));
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public long getAccessTokenRemainingTime(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    public String parseRefreshToken(String token) {
        return parseTokenSubject(token);
    }

    public JwtParseResult parseAccessTokenWithRemaining(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining <= 0) {
                return new JwtParseResult(null, -1);
            }
            return new JwtParseResult(claims.getSubject(), remaining);
        } catch (Exception e) {
            return new JwtParseResult(null, -1);
        }
    }

    public record JwtParseResult(String userId, long remainingTimeMs) {}

    private String createToken(String userId, String email, String username, Long expiration) {
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("username", username)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    private String parseTokenSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            // Jwts.parser() 已自动验证过期时间，过期的 token 会抛出 ExpiredJwtException
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey getSigningKey() {
        if (cachedSigningKey != null) {
            return cachedSigningKey;
        }
        synchronized (this) {
            if (cachedSigningKey != null) {
                return cachedSigningKey;
            }
            byte[] keyBytes = authProperties.getSecret().getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("JWT 签名密钥长度不足 32 字节，拒绝启动/签发 Token");
            }
            cachedSigningKey = Keys.hmacShaKeyFor(keyBytes);
            return cachedSigningKey;
        }
    }
}
