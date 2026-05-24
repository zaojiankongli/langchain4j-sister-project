package com.zjkl.ai.chat.stomp.dto;

/**
 * WebSocket 消息类型枚举
 */
public enum MessageType {
    // ========== 客户端 → 服务端 ==========
    
    /**
     * 聊天消息
     */
    CHAT,
    
    /**
     * 心跳请求
     */
    PING,
    
    /**
     * 心跳响应
     */
    PONG,
    
    /**
     * 文本消息
     */
    TEXT,
    
    /**
     * 语音消息（二进制）
     */
    AUDIO,
    
    /**
     * 错误消息
     */
    ERROR,
    
    /**
     * 系统消息（主动唤醒等）
     */
    SYSTEM,
    
    /**
     * 偷看请求（服务端请求客户端截图）
     */
    PEEK_REQUEST
}
