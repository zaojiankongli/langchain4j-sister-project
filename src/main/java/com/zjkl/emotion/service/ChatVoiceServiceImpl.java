package com.zjkl.emotion.service;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.ai.chat.service.SisterChatService;
import com.zjkl.emotion.model.EmotionalState;

import com.zjkl.emotion.model.VoiceParams;
import com.zjkl.emotion.util.AudioBuffer;
import com.zjkl.emotion.util.LlmResponseStreamParser;
import com.zjkl.ai.chat.stomp.ChatPushService;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 语音聊天
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatVoiceServiceImpl implements ChatVoiceService {

    private final TtsStreamingService ttsStreamingService;

    private final EmotionService emotionService;
    private final EmotionAnchorService anchorService;
    private final SisterChatService sisterChatService;
    private final ChatPushService chatPushService;
    private final ChatMemoryProvider redisChatMemoryProvider;
    private final ConverMessageService converMessageService;
    private final LlmResponseStreamParser parser;

    /**
     * 语音聊天
     */
    @Override
    public CompletableFuture<Void> chatWithVoice(String userId, String userInput, Boolean enableAudio, String imageUrl) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        log.info("开始 WebSocket 语音聊天：userId={}, userInput={}, enableAudio={}, imageUrl={}", userId, userInput, enableAudio, imageUrl);

        try {
            // 并行处理图片
            SisterChatService.ChatResult chatResult = sisterChatService.chatWithVoice(userInput, userId, imageUrl);
            Flux<String> llmStream = chatResult.stream();
            CompletableFuture<String> imageDescFuture = chatResult.imageDescFuture();

            // 解析响应
            LlmResponseStreamParser.ParsedResult result = parser.parse(llmStream);

            AtomicReference<SpeechSynthesizer> synthesizerRef = new AtomicReference<>();

            // 200ms 缓冲
            AudioBuffer audioBuffer = new AudioBuffer(200);

            StringBuilder replyCollector = new StringBuilder();

            result.getReplyStream()
                .concatMap(chunk -> {
                    chatPushService.pushText(userId, chunk, false);
                    replyCollector.append(chunk);
                    return Mono.empty();
                }, 1)
                .doOnComplete(() -> {
                    log.info("LLM 回复完成：userId={}", userId);
                    String fullReply = replyCollector.toString();
                    log.info("完整 reply: userId={}, length={}", userId, fullReply.length());

                    // 过滤括号内容
                    String ttsText = fullReply.replaceAll("[（(\\[【][^）)\\]】]*[）)\\]】]", "").trim();
                    if (log.isDebugEnabled()) {
                        log.debug("TTS 文本(已过滤括号内容): userId={}, originalLen={}, filteredLen={}",
                                userId, fullReply.length(), ttsText.length());
                    }

                    // 发送完成信号给前端
                    chatPushService.pushText(userId, "", true);

                    CompletableFuture.runAsync(() -> saveMemory(userId, userInput, imageUrl, imageDescFuture, fullReply));

                    SpeechSynthesizer synthesizer = synthesizerRef.get();
                    log.info("=== TTS 调试 === userId={}, enableAudio={}, synthesizer={}", userId, enableAudio, synthesizer);
                    if (synthesizer != null && Boolean.TRUE.equals(enableAudio)) {
                        log.info("开始 TTS 流式合成：userId={}, textLength={}", userId, fullReply.length());

                        AtomicReference<Throwable> ttsError = new AtomicReference<>();

                        CompletableFuture.runAsync(() -> {
                            try {
                                synthesizer.streamingCall(ttsText);
                                log.info("TTS 文本发送完成：userId={}", userId);
                                synthesizer.streamingComplete();
                                log.info("TTS streamingComplete 调用完成：userId={}", userId);
                            } catch (Exception e) {
                                log.error("TTS 合成失败：userId={}", userId, e);
                                ttsError.set(e);
                                chatPushService.pushError(userId, "语音合成失败，已跳过音频");
                                audioBuffer.markSynthesisCompleted();
                            } finally {
                                ttsStreamingService.closeSynthesizer(synthesizer);
                            }
                        }).orTimeout(30, TimeUnit.SECONDS).exceptionally(ex -> {
                            log.error("TTS 合成超时或失败：userId={}", userId, ex);
                            ttsError.set(ex instanceof TimeoutException
                                    ? new TimeoutException("语音合成超时（30s）") : ex);
                            audioBuffer.markSynthesisCompleted();
                            chatPushService.pushError(userId, "语音合成超时，已跳过音频");
                            ttsStreamingService.closeSynthesizer(synthesizer);
                            return null;
                        });

                        CompletableFuture.runAsync(() -> {
                            try {
                                audioBuffer.awaitPlaybackReady();

                                while (audioBuffer.hasMoreAudio()) {
                                    byte[] audioData = audioBuffer.getNextAudio(100);
                                    if (audioData != null) {
                                        chatPushService.pushAudio(userId, audioData);
                                        log.debug("已发送音频分片：userId={}, size={}", userId, audioData.length);
                                    }
                                }

                                log.info("音频播放完成：userId={}", userId);
                                Throwable ttsErr = ttsError.get();
                                if (ttsErr != null) {
                                    future.completeExceptionally(ttsErr);
                                } else {
                                    future.complete(null);
                                }

                            } catch (Exception e) {
                                log.error("音频播放失败：userId={}", userId, e);
                                future.completeExceptionally(e);
                            }
                        }).orTimeout(60, TimeUnit.SECONDS).exceptionally(ex -> {
                            log.error("音频播放超时或失败：userId={}", userId, ex);
                            future.completeExceptionally(ex instanceof TimeoutException
                                    ? new TimeoutException("音频播放超时（60s）") : ex);
                            return null;
                        });

                    } else {
                        future.complete(null);
                    }
                })
                .doOnError(error -> {
                    log.error("LLM 回复流错误：userId={}", userId, error);
                    chatPushService.pushError(userId, "LLM 回复流错误：" + error.getMessage());
                    future.completeExceptionally(error);
                })
                .subscribe();

            // 就绪后初始化 TTS
            result.getVoiceParams()
                .doOnSuccess(params -> {
                    log.info("voice_params 已解析：userId={}, volume={}", userId, params.getVolume());

                    if (Boolean.TRUE.equals(enableAudio)) {
                        SpeechSynthesizer synthesizer = ttsStreamingService.initTtsSynthesizer(userId, params, audioBuffer);
                        if (synthesizer != null) {
                            synthesizerRef.set(synthesizer);
                            log.info("TTS 已就绪：userId={}", userId);
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("voice_params 解析失败：userId={}", userId, error);
                    chatPushService.pushError(userId, "响应解析失败");
                })
                .subscribe();

            // 6. 订阅 delta_emotion（后台更新用户情绪 + 触发锚点监测）
            result.getDeltaEmotion()
                .doOnSuccess(delta -> {
                    // 获取更新前的情绪状态
                    EmotionalState oldEmotion = emotionService.getUserEmotion(userId);
                    // 更新情绪
                    EmotionalState newEmotion = emotionService.updateUserEmotion(userId, delta);
                    log.debug("用户情绪已更新：userId={}, P={}, A={}, D={}",
                        userId, newEmotion.getPleasure(), newEmotion.getArousal(), newEmotion.getDominance());
                    // 触发锚点监测（同时更新最后消息时间）
                    anchorService.onEmotionChange(userId, oldEmotion, newEmotion);
                })
                .doOnError(error -> {
                    log.warn("情绪更新失败：userId={}", userId, error);
                })
                .subscribe();

        } catch (Exception e) {
            log.error("语音聊天失败：userId={}", userId, e);
            chatPushService.pushError(userId, "服务繁忙，请稍后重试");
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 保存聊天记录
     */
    private void saveMemory(String userId, String userInput, String imageUrl,
                           CompletableFuture<String> imageDescFuture, String fullReply) {
        try {
            // Redis
            
            // 拼接描述
            String redisUserText = userInput;
            if (imageUrl != null && !imageUrl.isBlank() && imageDescFuture != null) {
                try {
                    String imageDesc = imageDescFuture.get(3, TimeUnit.SECONDS);
                    redisUserText += " [图片:" + imageDesc + "|" + imageUrl + "]";
                    log.debug("VLM 描述获取成功: {}", imageDesc);
                } catch (Exception e) {
                    log.warn("VLM 描述获取超时或失败，使用原始文本: {}", e.getMessage());
                }
            }

            // 保存到 Redis（user → assistant 顺序保证）
            try {
                var chatMemory = redisChatMemoryProvider.get(userId);
                chatMemory.add(UserMessage.from(redisUserText));
                chatMemory.add(AiMessage.from(fullReply));
                log.debug("Redis 记忆已保存: userId={}", userId);
            } catch (Exception e) {
                log.error("Redis 记忆保存失败: userId={}", userId, e);
            }

            // MySQL

            // 构建用户消息内容
            List<MessageContent> userContents = new ArrayList<>();
            userContents.add(MessageContent.text(userInput));
            if (imageUrl != null && !imageUrl.isBlank()) {
                userContents.add(MessageContent.image(imageUrl));
            }

            // 保存用户消息
            converMessageService.saveMessage(userId, "user", userContents);

            // 保存回复
            List<MessageContent> aiContents = List.of(MessageContent.text(fullReply));
            converMessageService.saveMessage(userId, "assistant", aiContents);

            log.debug("MySQL 消息已保存: userId={}, userContents={}, aiContents={}",
                userId, userContents.size(), aiContents.size());

        } catch (Exception e) {
            log.error("保存记忆失败: userId={}", userId, e);
        }
    }

}