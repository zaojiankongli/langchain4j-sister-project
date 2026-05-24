package com.zjkl.common.interceptor;

import com.zjkl.auth.util.UserContext;
import com.zjkl.common.util.JwtUtil;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final String NEW_ACCESS_TOKEN_HEADER = "New-Access-Token";

    /** 需要放行的路径前缀 */
    private static final java.util.List<String> PUBLIC_PATHS = java.util.List.of(
        "/api/common/", "/ws/"
    );

    private final JwtUtil jwtUtil;
    private final UserContext userContext;
    private final UserMapper userMapper;

    public AuthInterceptor(JwtUtil jwtUtil, UserContext userContext, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userContext = userContext;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 放行公开路径
        for (String prefix : PUBLIC_PATHS) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }

        String authorization = request.getHeader("Authorization");

        if (authorization == null || authorization.isEmpty()) {
            log.warn("未认证请求已被拦截：{}", uri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录，请先登录\"}");
            return false;
        }

        String token = extractToken(authorization);
        if (token == null) {
            log.warn("Authorization 格式错误：{}", authorization);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token 格式错误\"}");
            return false;
        }

        JwtUtil.JwtParseResult parseResult = jwtUtil.parseAccessTokenWithRemaining(token);
        if (parseResult.userId() == null) {
            log.warn("Token 无效或已过期");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"Token 无效或已过期\"}");
            return false;
        }

        userContext.setUserId(parseResult.userId());
        log.debug("用户认证成功：userId={}", parseResult.userId());

        if (parseResult.remainingTimeMs() < jwtUtil.getAccessTokenExpiration() / 2) {
            User user = userMapper.findById(parseResult.userId());
            if (user != null) {
                String newAccessToken = jwtUtil.generateAccessToken(user);
                response.setHeader(NEW_ACCESS_TOKEN_HEADER, newAccessToken);
                log.debug("Access token 已刷新，剩余有效期：{}ms", parseResult.remainingTimeMs());
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
}
