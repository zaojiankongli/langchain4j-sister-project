package com.zjkl.ai.chat.stomp;

import com.zjkl.ai.chat.stomp.dto.ChatRequest;
import com.zjkl.emotion.service.ChatVoiceService;
import com.zjkl.wakeup.tracker.WakeUpTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * STOMP WebSocket 控制器
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatVoiceService chatVoiceService;
    private final ChatPushService chatPushService;
    private final WakeUpTracker wakeUpTracker;

    /**
     * 处理聊天消息
     * 客户端：SEND /app/chat
     * 消息体：ChatRequest { text, enableAudio }
     */
    @MessageMapping("/chat")
    public void handleChat(ChatRequest request, Principal principal) {
        String userId = principal.getName();
        String text = request.getText();
        Boolean enableAudio = request.getEnableAudio();

        if (text == null || text.isEmpty()) {
            chatPushService.pushError(userId, "消息内容不能为空");
            return;
        }

        log.info("收到聊天消息：userId={}, text={}, enableAudio={}, imageUrl={}", userId, text, enableAudio, request.getImageUrl());

        wakeUpTracker.markUserReplied(userId);

        chatVoiceService.chatWithVoice(userId, text, enableAudio, request.getImageUrl())
                .exceptionally(error -> {
                    log.error("聊天处理失败：userId={}", userId, error);
                    chatPushService.pushError(userId, "处理失败：" + error.getMessage());
                    return null;
                });
    }

    /**
     * 处理心跳
     * 客户端：SEND /app/ping
     * 服务端：通过 /user/{userId}/queue/control 推送 PONG
     */
    @MessageMapping("/ping")
    public void handlePing(Principal principal) {
        String userId = principal.getName();
        log.debug("收到心跳：userId={}", userId);
        chatPushService.pushPong(userId);
    }

    /**
     * STOMP 会话建立事件（连接成功）
     */
    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal != null) {
            String userId = principal.getName();
            log.info("STOMP 会话建立：userId={}", userId);

            // 注册用户连接（启动消息队列和发送线程）
            chatPushService.onUserConnected(userId);

            // 发送认证成功消息
            chatPushService.pushAuthSuccess(userId);
            // 发送欢迎消息
            chatPushService.pushSystem(userId, "欢迎使用语音聊天！");
        }
    }

    /**
     * STOMP 会话断开事件
     */
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal != null) {
            String userId = principal.getName();
            log.info("STOMP 会话断开：userId={}", userId);

            // 标记用户断开（延迟清理队列，给 TTS 回调时间完成）
            chatPushService.onUserDisconnected(userId);
        }
    }
}
