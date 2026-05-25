package com.zjkl.emotion.service;

import java.util.concurrent.CompletableFuture;

/**
 * 语音聊天服务接口
 */
public interface ChatVoiceService {
    /**
     * 语音聊天
     * @param userId 用户ID
     * @param userInput 用户输入
     * @param enableAudio 是否启用音频
     * @param imageUrl 图片URL
     * @return CompletableFuture
     */
    CompletableFuture<Void> chatWithVoice(String userId, String userInput, Boolean enableAudio, String imageUrl);
}