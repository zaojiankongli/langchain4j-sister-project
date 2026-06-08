package com.zjkl.miniprogram.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.stomp.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MiniprogramRealtimeSocketRegistry {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void add(String userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        session.getAttributes().put("userId", userId);
        log.debug("小程序实时 WebSocket 已连接: userId={}, sessionId={}", userId, session.getId());
    }

    public void remove(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (!(userId instanceof String value)) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByUser.get(value);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(value);
        }
        log.debug("小程序实时 WebSocket 已断开: userId={}, sessionId={}", value, session.getId());
    }

    public void push(String userId, WebSocketMessage message) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.warn("小程序实时消息序列化失败: userId={}, type={}", userId, message.getType(), e);
            return;
        }

        TextMessage textMessage = new TextMessage(payload);
        sessions.removeIf(session -> !send(session, textMessage));
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId);
        }
    }

    private boolean send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(message);
            }
            return true;
        } catch (IOException e) {
            log.warn("小程序实时消息发送失败: sessionId={}", session.getId(), e);
            return false;
        }
    }
}
