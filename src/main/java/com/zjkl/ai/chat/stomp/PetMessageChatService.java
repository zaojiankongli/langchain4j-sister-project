package com.zjkl.ai.chat.stomp;

import com.zjkl.ai.chat.stomp.dto.ChatRequest;
import com.zjkl.common.ErrorCode;
import com.zjkl.common.exception.BusinessException;
import com.zjkl.common.util.RateLimiter;
import com.zjkl.emotion.service.ChatVoiceService;
import com.zjkl.wakeup.tracker.WakeUpTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PetMessageChatService {

    private final ChatVoiceService chatVoiceService;
    private final ChatPushService chatPushService;
    private final WakeUpTracker wakeUpTracker;
    private final RateLimiter rateLimiter;

    public void handleChat(String userId, ChatRequest request) {
        String text = request.getText();
        Boolean enableAudio = request.getEnableAudio();
        String imageUrl = request.getImageUrl();

        if (imageUrl != null && imageUrl.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片URL过长");
        }

        if (!rateLimiter.tryAcquire("rate:ws-chat:" + userId, 10, 60_000)) {
            chatPushService.pushError(userId, "消息发送过于频繁，请稍后再试");
            return;
        }

        if ((text == null || text.isEmpty()) && (imageUrl == null || imageUrl.isEmpty())) {
            chatPushService.pushError(userId, "消息内容不能为空");
            return;
        }
        if (text != null && text.length() > 200) {
            chatPushService.pushError(userId, "消息文本不能超过200个字符");
            return;
        }
        if (text == null) {
            text = "";
        }

        log.info("收到聊天消息：userId={}, text=***, enableAudio={}", userId, enableAudio);

        wakeUpTracker.markUserReplied(userId);

        chatVoiceService.chatWithVoice(userId, text, enableAudio, imageUrl)
                .exceptionally(error -> {
                    log.error("聊天处理失败：userId={}", userId, error);
                    chatPushService.pushError(userId, "处理失败，请稍后重试");
                    return null;
                });
    }
}
