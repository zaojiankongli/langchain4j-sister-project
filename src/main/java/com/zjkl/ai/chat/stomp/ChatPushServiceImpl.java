package com.zjkl.ai.chat.stomp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * STOMP 消息推送实现类
 * 将连接状态管理和心跳检查委托给专门的组件
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatPushServiceImpl implements ChatPushService {

    private final ConnectionStateManager connectionStateManager;
    private final HeartbeatChecker heartbeatChecker;

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
    }

    @Override
    public void pushSystem(String userId, String content) {
        connectionStateManager.pushSystem(userId, content);
    }

    @Override
    public void pushError(String userId, String errMsg) {
        connectionStateManager.pushError(userId, errMsg);
    }

    @Override
    public void pushAuthSuccess(String userId) {
        connectionStateManager.pushAuthSuccess(userId);
    }

    @Override
    public void pushAudio(String userId, byte[] audioData) {
        connectionStateManager.pushAudio(userId, audioData);
    }

    @Override
    public void pushPong(String userId) {
        connectionStateManager.pushPong(userId);
    }

    @Override
    public void onUserConnected(String userId) {
        connectionStateManager.onUserConnected(userId);
        heartbeatChecker.updateActiveTime(userId);
    }

    @Override
    public void onUserDisconnected(String userId) {
        connectionStateManager.onUserDisconnected(userId);
    }

    @Override
    public void updateActiveTime(String userId) {
        heartbeatChecker.updateActiveTime(userId);
    }
}