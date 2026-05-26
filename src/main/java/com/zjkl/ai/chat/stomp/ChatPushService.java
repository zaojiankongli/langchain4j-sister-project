package com.zjkl.ai.chat.stomp;

/**
 * STOMP 消息推送接口
 */
public interface ChatPushService {

    boolean isUserConnected(String userId);

    void pushPeekRequest(String userId, String peekId);

    void pushText(String userId, String content, boolean isComplete);

    void pushSystem(String userId, String content);

    void pushError(String userId, String errMsg);

    void pushAuthSuccess(String userId);

    void pushAudio(String userId, byte[] audioData);

    void pushPong(String userId);

    void onUserConnected(String userId);

    void onUserDisconnected(String userId);

    void updateActiveTime(String userId);
}
