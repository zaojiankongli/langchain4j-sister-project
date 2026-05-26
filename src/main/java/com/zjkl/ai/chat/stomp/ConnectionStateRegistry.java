package com.zjkl.ai.chat.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接状态注册表
 * 管理每个用户的连接状态（CONNECTED / DISCONNECTED）
 */
@Component
@Slf4j
public class ConnectionStateRegistry {

    public enum ConnectionState {
        CONNECTED,
        DISCONNECTED
    }

    private final ConcurrentHashMap<String, ConnectionState> connectionStates = new ConcurrentHashMap<>();

    /**
     * 判断用户是否已连接
     */
    public boolean isConnected(String userId) {
        return connectionStates.get(userId) == ConnectionState.CONNECTED;
    }

    /**
     * 获取用户连接状态
     */
    public ConnectionState getState(String userId) {
        return connectionStates.get(userId);
    }

    /**
     * 设置用户为已连接
     */
    public void setConnected(String userId) {
        connectionStates.put(userId, ConnectionState.CONNECTED);
    }

    /**
     * 设置用户为已断开
     */
    public void setDisconnected(String userId) {
        connectionStates.put(userId, ConnectionState.DISCONNECTED);
    }

    /**
     * 移除用户状态
     */
    public void removeUser(String userId) {
        connectionStates.remove(userId);
    }

    /**
     * 清空所有状态
     */
    public void clearAll() {
        connectionStates.clear();
    }
}
