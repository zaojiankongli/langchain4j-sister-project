package com.zjkl.ai.chat.realtime;

import com.zjkl.ai.chat.realtime.dto.PetRealtimeAudioChunk;
import com.zjkl.ai.chat.realtime.dto.PetRealtimeStartRequest;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.common.ErrorCode;
import com.zjkl.common.exception.BusinessException;
import com.zjkl.common.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 桌宠 Qwen-Omni-Realtime STOMP 控制器。
 * <p>
 * 该入口独立于 /app/chat，避免桌宠误入网页端传统 TTS 链路。
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PetRealtimeStompController {

    private static final int MAX_AUDIO_BASE64_CHARS = 64_000;
    private static final int MAX_AUDIO_CHUNKS_PER_MINUTE = 900;

    private final DesktopOmniRealtimeSessionService realtimeSessionService;
    private final ChatPushService chatPushService;
    private final RateLimiter rateLimiter;

    @MessageMapping("/pet/realtime/start")
    public void start(PetRealtimeStartRequest request, Principal principal) {
        String userId = requireUserId(principal);
        if (!rateLimiter.tryAcquire("rate:pet-realtime-start:" + userId, 20, 60_000)) {
            chatPushService.pushError(userId, "实时语音启动过于频繁，请稍后再试");
            return;
        }

        realtimeSessionService.start(userId, request != null ? request : new PetRealtimeStartRequest());
    }

    @MessageMapping("/pet/realtime/audio")
    public void audio(PetRealtimeAudioChunk chunk, Principal principal) {
        String userId = requireUserId(principal);
        if (chunk == null || chunk.getAudioBase64() == null || chunk.getAudioBase64().isBlank()) {
            return;
        }
        if (chunk.getAudioBase64().length() > MAX_AUDIO_BASE64_CHARS) {
            chatPushService.pushError(userId, "音频分片过大");
            return;
        }
        if (!rateLimiter.tryAcquire("rate:pet-realtime-audio:" + userId, MAX_AUDIO_CHUNKS_PER_MINUTE, 60_000)) {
            chatPushService.pushError(userId, "实时语音上行过于频繁，请稍后再试");
            return;
        }

        realtimeSessionService.appendAudio(userId, chunk.getAudioBase64());
    }

    @MessageMapping("/pet/realtime/stop")
    public void stop(Principal principal) {
        String userId = requireUserId(principal);
        realtimeSessionService.stop(userId);
    }

    private String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return principal.getName();
    }
}
