package com.zjkl.ai.chat.stomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 音频消息 DTO
 * 用于 STOMP 传输音频数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioMessage {
    
    /**
     * 音频数据（Base64 编码字符串）
     */
    private String data;
}