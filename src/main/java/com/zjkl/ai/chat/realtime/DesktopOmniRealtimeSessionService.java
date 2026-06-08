package com.zjkl.ai.chat.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeStartRequest;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.common.config.properties.AiProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理桌宠用户到 Qwen-Omni-Realtime 的一对一会话。
 */
@Service
@Slf4j
public class DesktopOmniRealtimeSessionService {

    private final AiProperties aiProperties;
    private final ChatPushService chatPushService;
    private final ConverMessageService converMessageService;
    private final ObjectMapper objectMapper;
    private final Executor asyncExecutor;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ConcurrentHashMap<String, DesktopOmniRealtimeSession> sessions = new ConcurrentHashMap<>();

    public DesktopOmniRealtimeSessionService(AiProperties aiProperties, ChatPushService chatPushService, ConverMessageService converMessageService, ObjectMapper objectMapper,@Qualifier("asyncTaskExecutor") Executor asyncExecutor) {
        this.aiProperties = aiProperties;
        this.chatPushService = chatPushService;
        this.converMessageService = converMessageService;
        this.objectMapper = objectMapper;
        this.asyncExecutor = asyncExecutor;
    }

    public void start(String userId, PetRealtimeStartRequest request) {
        sessions.compute(userId, (key, existing) -> {
            if (existing != null) {
                existing.close("restart");
            }

            AtomicReference<DesktopOmniRealtimeSession> holder = new AtomicReference<>();
            DesktopOmniRealtimeSession session = new DesktopOmniRealtimeSession(
                    userId,
                    request,
                    aiProperties,
                    chatPushService,
                    converMessageService,
                    objectMapper,
                    httpClient,
                    asyncExecutor,
                    () -> sessions.remove(userId, holder.get())
            );
            holder.set(session);
            session.connect();
            return session;
        });
    }

    public void appendAudio(String userId, String audioBase64) {
        DesktopOmniRealtimeSession session = sessions.get(userId);
        if (session == null) {
            chatPushService.pushError(userId, "实时语音会话未启动");
            return;
        }
        session.appendAudio(audioBase64);
    }

    public void stop(String userId) {
        DesktopOmniRealtimeSession session = sessions.remove(userId);
        if (session != null) {
            session.close("client stop");
        }
        chatPushService.pushPetMotion(userId, "idle", "normal");
        chatPushService.pushSystem(userId, "实时语音已停止");
    }

    @PreDestroy
    public void stopAll() {
        sessions.forEach((userId, session) -> session.close("server shutdown"));
        sessions.clear();
    }
}
