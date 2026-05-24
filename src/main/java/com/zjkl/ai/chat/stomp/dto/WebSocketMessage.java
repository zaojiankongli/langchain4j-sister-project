package com.zjkl.ai.chat.stomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Map.of;

/**
 * WebSocket 消息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    
    /**
     * 消息类型
     */
    private MessageType type;
    
    /**
     * 消息负载
     */
    private Map<String, Object> payload;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 消息 ID
     */
    private String messageId;
    
    /**
     * 构造函数
     */
    public WebSocketMessage(MessageType type, Object content) {
        this.type = type;
        this.payload = new HashMap<>();
        if (content instanceof Map) {
            this.payload.putAll((Map<String, Object>) content);
        } else {
            this.payload.put("content", content);
        }
        this.timestamp = System.currentTimeMillis();
        this.messageId = UUID.randomUUID().toString();
    }
    
    /**
     * 创建聊天消息
     */
    public static WebSocketMessage chat(String text) {
        return new WebSocketMessage(MessageType.CHAT, of("text", text));
    }
    
    /**
     * 创建文本消息
     */
    public static WebSocketMessage text(String content, boolean isComplete) {
        return new WebSocketMessage(MessageType.TEXT, of("content", content, "isComplete", isComplete));
    }
    
    /**
     * 创建错误消息
     */
    public static WebSocketMessage error(String message) {
        return new WebSocketMessage(MessageType.ERROR, of("message", message));
    }
    
    /**
     * 创建系统消息
     */
    public static WebSocketMessage system(String content) {
        return new WebSocketMessage(MessageType.SYSTEM, of("content", content));
    }
}
