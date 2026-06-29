package com.zjkl.miniprogram.realtime;

import com.zjkl.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
@Slf4j
public class MiniprogramRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";

    private final JwtUtil jwtUtil;
    private final MiniprogramRealtimeSocketRegistry socketRegistry;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        String userId = token == null ? null : jwtUtil.parseAccessToken(token);
        if (userId == null || userId.isBlank()) {
            session.close(new CloseStatus(HttpStatus.UNAUTHORIZED.value(), "unauthorized"));
            return;
        }
        // 检查 Token 是否在黑名单中（已 logout 的 Token 不允许建立 WebSocket）
        if (isTokenBlacklisted(token)) {
            log.warn("Token 已被拉黑，拒绝 WebSocket 连接：userId={}", userId);
            session.close(new CloseStatus(HttpStatus.UNAUTHORIZED.value(), "token revoked"));
            return;
        }
        socketRegistry.add(userId, session);
    }

    private boolean isTokenBlacklisted(String token) {
        try {
            String tokenHash = com.zjkl.common.util.HashUtil.sha256Hex(token);
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + tokenHash;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
        } catch (Exception e) {
            log.warn("Token 黑名单检查失败，放行连接", e);
            return false;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        socketRegistry.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        socketRegistry.remove(session);
        log.debug("小程序实时 WebSocket 传输异常: sessionId={}", session.getId(), exception);
    }

    private String extractToken(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst("token");
    }
}
