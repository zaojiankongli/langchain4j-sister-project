package com.zjkl.common.interceptor;

import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.util.JwtUtil;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

/**
 * JWT 认证拦截器
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final String NEW_ACCESS_TOKEN_HEADER = "New-Access-Token";
    private static final String REFRESH_THROTTLE_KEY_PREFIX = "auth:refresh:";
    private static final Duration REFRESH_THROTTLE_TTL = Duration.ofMinutes(5);
    private static final String UNAUTHORIZED_BODY = "{\"code\":401,\"message\":\"请先登录\"}";

    private final JwtUtil jwtUtil;
    private final UserContext userContext;
    private final UserMapper userMapper;
    private final AuthProperties authProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public AuthInterceptor(JwtUtil jwtUtil, UserContext userContext, UserMapper userMapper,
                           AuthProperties authProperties, StringRedisTemplate stringRedisTemplate) {
        this.jwtUtil = jwtUtil;
        this.userContext = userContext;
        this.userMapper = userMapper;
        this.authProperties = authProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 白名单路径已在 WebMvcConfig.excludePathPatterns 中排除，此处不再重复检查

        String authorization = request.getHeader("Authorization");

        if (authorization == null || authorization.isEmpty()) {
            log.warn("未认证请求已被拦截：{}", uri);
            writeUnauthorized(response);
            return false;
        }

        String token = extractToken(authorization);
        if (token == null) {
            log.warn("Authorization 格式错误：{}...", authorization.length() > 15 ? authorization.substring(0, 15) : authorization);
            writeUnauthorized(response);
            return false;
        }

        JwtUtil.JwtParseResult parseResult = jwtUtil.parseAccessTokenWithRemaining(token);
        if (parseResult.userId() == null) {
            log.warn("Token 无效或已过期");
            writeUnauthorized(response);
            return false;
        }

        // 检查 token 是否已被吊销（黑名单，使用 SHA-256 哈希值作为 key）
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey("auth:token:blacklist:" + sha256(token)))) {
            log.warn("Token 已被吊销：userId={}", parseResult.userId());
            writeUnauthorized(response);
            return false;
        }

        userContext.setUserId(parseResult.userId());
        log.debug("用户认证成功：userId={}", parseResult.userId());

        if (parseResult.remainingTimeMs() < jwtUtil.getAccessTokenExpiration() / 2) {
            String throttleKey = REFRESH_THROTTLE_KEY_PREFIX + parseResult.userId();
            Boolean notRecentlyRefreshed = stringRedisTemplate.opsForValue()
                    .setIfAbsent(throttleKey, "1", REFRESH_THROTTLE_TTL);
            if (Boolean.TRUE.equals(notRecentlyRefreshed)) {
                // 从 JWT claims 中提取用户信息，避免查询 DB
                Map<String, String> claims = jwtUtil.parseAccessTokenClaims(token);
                if (claims != null && claims.get("userId") != null) {
                    String newAccessToken = jwtUtil.generateAccessToken(
                            claims.get("userId"), claims.get("email"), claims.get("username"));
                    response.setHeader(NEW_ACCESS_TOKEN_HEADER, newAccessToken);
                    log.debug("Access token 已刷新，剩余有效期：{}ms", parseResult.remainingTimeMs());
                }
            }
        }

        return true;
    }

    /**
     * 请求完成后清理 UserContext
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        userContext.clear();
        log.debug("用户上下文已清理");
    }

    /**
     * 从 Authorization 头提取 token
     */
    private String extractToken(String authorization) {
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
