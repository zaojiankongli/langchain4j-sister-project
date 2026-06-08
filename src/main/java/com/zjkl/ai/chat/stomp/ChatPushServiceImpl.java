package com.zjkl.ai.chat.stomp;

import com.zjkl.ai.chat.stomp.dto.MessageType;
import com.zjkl.ai.chat.stomp.dto.WebSocketMessage;
import com.zjkl.miniprogram.realtime.MiniprogramRealtimeSocketRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

/**
 * STOMP 消息推送实现类
 *
 * 大部分 push 方法直接委托给 ConnectionStateManager。
 * onUserConnected / onUserDisconnected 负责协调 ConnectionStateManager（连接/队列管理）
 * 和 HeartbeatChecker（心跳计时器）两个组件的生命周期。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatPushServiceImpl implements ChatPushService {

    private final ConnectionStateManager connectionStateManager;
    private final HeartbeatChecker heartbeatChecker;
    private final MiniprogramRealtimeSocketRegistry miniprogramSocketRegistry;

    // ==================== ChatPushService 接口实现 ====================

    @Override
    public boolean isUserConnected(String userId) {
        return connectionStateManager.isUserConnected(userId);
    }

    @Override
    public void pushPeekRequest(String userId, String peekId) {
        connectionStateManager.pushPeekRequest(userId, peekId);
    }

    @Override
    public void pushText(String userId, String content, boolean isComplete) {
        connectionStateManager.pushText(userId, content, isComplete);
        pushToMiniprogram(userId, WebSocketMessage.text(content, isComplete));
    }

    @Override
    public void pushPetExpression(String userId, String expression, double intensity, long durationMs) {
        connectionStateManager.pushPetExpression(userId, expression, intensity, durationMs);
        pushToMiniprogram(userId, WebSocketMessage.petExpression(expression, intensity, durationMs));
    }

    @Override
    public void pushPetMotion(String userId, String motion, String priority) {
        connectionStateManager.pushPetMotion(userId, motion, priority);
        pushToMiniprogram(userId, WebSocketMessage.petMotion(motion, priority));
    }

    @Override
    public void pushEmotionUpdate(String userId, double pleasure, double arousal, double dominance, String moodLabel, String moodDescription) {
        connectionStateManager.pushEmotionUpdate(userId, pleasure, arousal, dominance, moodLabel, moodDescription);
        pushToMiniprogram(userId, WebSocketMessage.emotionUpdate(Map.of(
                "pleasure", pleasure,
                "arousal", arousal,
                "dominance", dominance,
                "moodLabel", moodLabel,
                "moodDescription", moodDescription
        )));
    }

    @Override
    public void pushSystem(String userId, String content) {
        connectionStateManager.pushSystem(userId, content);
        pushToMiniprogram(userId, WebSocketMessage.system(content));
    }

    @Override
    public void pushError(String userId, String errMsg) {
        connectionStateManager.pushError(userId, errMsg);
        pushToMiniprogram(userId, WebSocketMessage.error(errMsg));
    }

    @Override
    public void pushAuthSuccess(String userId) {
        connectionStateManager.pushAuthSuccess(userId);
    }

    @Override
    public void pushAudio(String userId, byte[] audioData) {
        connectionStateManager.pushAudio(userId, audioData);
        pushToMiniprogram(userId, new WebSocketMessage(MessageType.AUDIO, Map.of(
                "audioData", Base64.getEncoder().encodeToString(audioData)
        )));
    }

    @Override
    public void pushPong(String userId) {
        connectionStateManager.pushPong(userId);
    }

    private void pushToMiniprogram(String userId, WebSocketMessage message) {
        miniprogramSocketRegistry.push(userId, message);
    }

    @Override
    public void onUserConnected(String userId) {
        connectionStateManager.onUserConnected(userId);
        heartbeatChecker.updateActiveTime(userId);
    }

    @Override
    public void onUserDisconnected(String userId) {
        heartbeatChecker.clearActiveTime(userId);
        connectionStateManager.onUserDisconnected(userId);
    }

    @Override
    public void updateActiveTime(String userId) {
        heartbeatChecker.updateActiveTime(userId);
    }
}
