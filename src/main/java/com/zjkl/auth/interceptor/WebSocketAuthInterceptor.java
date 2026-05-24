package com.zjkl.auth.interceptor;

import com.zjkl.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手认证拦截器
 */
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        log.debug("开始 WebSocket 握手认证...");

        String uri = request.getURI().toString();
        String[] parts = uri.split("\\?");

        if (parts.length < 2) {
            log.warn("握手失败：缺少 URL 参数");
            return false;
        }

        String[] params = parts[1].split("&");
        String token = null;

        for (String param : params) {
            String[] kv = param.split("=");
            if (kv.length == 2 && "token".equals(kv[0])) {
                try {
                    token = java.net.URLDecoder.decode(kv[1], "UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    log.warn("Token 解码失败：{}", e.getMessage());
                    return false;
                }
                break;
            }
        }

        if (token == null || token.isEmpty()) {
            log.warn("握手失败：Token 为空");
            return false;
        }

        log.debug("提取到 Token: {}...", token.substring(0, Math.min(20, token.length())));

        try {
            String userId = jwtUtil.parseAccessToken(token);
            if (userId == null || userId.isEmpty()) {
                // accessToken 过期，尝试用 refreshToken
                userId = jwtUtil.parseRefreshToken(token);
            }
            if (userId == null || userId.isEmpty()) {
                log.warn("握手失败：Token 无效或已过期，拒绝连接");
                return false;
            }

            attributes.put("userId", userId);
            attributes.put("authenticated", true);

            log.info("握手认证成功，userId={}", userId);
            return true;

        } catch (Exception e) {
            log.warn("握手失败：{}", e.getMessage());
            log.debug("Token 验证异常", e);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.warn("握手后异常：{}", exception.getMessage());
            log.debug("握手后异常详情", exception);
        }
    }
}
