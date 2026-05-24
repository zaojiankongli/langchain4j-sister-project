package com.zjkl.ai.chat.stomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求 DTO
 * 客户端 SEND 到 /app/chat 时使用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    /**
     * 用户输入文本
     */
    private String text;
    
    /**
     * 是否启用语音
     */
    private Boolean enableAudio;

    /**
     * 图片 URL（可选，聊天时发送的图片）
     */
    private String imageUrl;
}