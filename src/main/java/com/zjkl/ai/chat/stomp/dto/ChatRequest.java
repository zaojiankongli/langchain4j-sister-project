package com.zjkl.ai.chat.stomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求 DTO
 * 客户端 SEND 到 /app/chat 时使用
 *
 * 注意：STOMP 消息不经过 Jakarta Bean Validation，@Size 等注解无效。
 * 输入校验由 ChatStompController.handleChat() 手动执行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    /**
     * 用户输入文本（最大 200 字符，由 ChatStompController 校验）
     */
    private String text;
    
    /**
     * 是否启用语音
     */
    private Boolean enableAudio;

    /**
     * 图片 URL（可选，聊天时发送的图片，最大 500 字符，由 ChatStompController 校验）
     */
    private String imageUrl;
}