package com.zjkl.miniprogram.realtime;

import com.zjkl.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final JwtUtil jwtUtil;
    private final MiniprogramRealtimeSocketRegistry socketRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        String userId = token == null ? null : jwtUtil.parseAccessToken(token);
        if (userId == null || userId.isBlank()) {
            session.close(new CloseStatus(HttpStatus.UNAUTHORIZED.value(), "unauthorized"));
            return;
        }
        socketRegistry.add(userId, session);
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
