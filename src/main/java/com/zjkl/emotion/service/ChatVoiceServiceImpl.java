package com.zjkl.emotion.service;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.zjkl.ai.chat.service.SisterChatService;
import com.zjkl.anchor.service.AnchorEventService;
import com.zjkl.emotion.model.EmotionalState;

import com.zjkl.emotion.util.AudioBuffer;
import com.zjkl.emotion.util.LlmResponseStreamParser;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.chat.stomp.SemanticPetEventAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 语音聊天
 */
@Slf4j
@Service
public class ChatVoiceServiceImpl implements ChatVoiceService {

    private final TtsStreamingService ttsStreamingService;
    private final EmotionService emotionService;
    private final AnchorEventService anchorService;
    private final SisterChatService sisterChatService;
    private final ChatPushService chatPushService;
    private final SemanticPetEventAdapter semanticPetEventAdapter;
    private final ChatReplyPersistenceService chatReplyPersistenceService;
    private final ChatAudioPlaybackService chatAudioPlaybackService;
    private final LlmResponseStreamParser parser;
    private final Executor asyncExecutor;

    public ChatVoiceServiceImpl(TtsStreamingService ttsStreamingService, EmotionService emotionService, AnchorEventService anchorService, SisterChatService sisterChatService, ChatPushService chatPushService, SemanticPetEventAdapter semanticPetEventAdapter, ChatReplyPersistenceService chatReplyPersistenceService, ChatAudioPlaybackService chatAudioPlaybackService, LlmResponseStreamParser parser,@Qualifier("asyncTaskExecutor")  Executor asyncExecutor) {
        this.ttsStreamingService = ttsStreamingService;
        this.emotionService = emotionService;
        this.anchorService = anchorService;
        this.sisterChatService = sisterChatService;
        this.chatPushService = chatPushService;
        this.semanticPetEventAdapter = semanticPetEventAdapter;
        this.chatReplyPersistenceService = chatReplyPersistenceService;
        this.chatAudioPlaybackService = chatAudioPlaybackService;
        this.parser = parser;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 语音聊天
     */
    @Override
    public CompletableFuture<Void> chatWithVoice(String userId, String userInput, Boolean enableAudio, String imageUrl) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        log.info("开始 WebSocket 语音聊天：userId={}, userInput=***, enableAudio={}", userId, enableAudio);

        try {
            semanticPetEventAdapter.pushChatPhase(userId, SemanticPetEventAdapter.ChatPhase.THINKING);

            SisterChatService.ChatResult chatResult = sisterChatService.chatWithVoice(userInput, userId, imageUrl);
            LlmResponseStreamParser.ParsedResult result = parser.parse(chatResult.stream());

            AtomicReference<SpeechSynthesizer> synthesizerRef = new AtomicReference<>();
            AudioBuffer audioBuffer = new AudioBuffer(200);
            List<String> replyChunks = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            AtomicReference<Boolean> firstChunkSent = new AtomicReference<>(false);

            var replyChain = buildReplyChain(userId, userInput, imageUrl, enableAudio, chatResult.imageDescFuture(),
                    result, future, synthesizerRef, audioBuffer, replyChunks, firstChunkSent);

            subscribeVoiceParams(userId, enableAudio, result, synthesizerRef, audioBuffer, replyChain);
            subscribeDeltaEmotion(userId, result);

        } catch (Exception e) {
            log.error("语音聊天失败：userId={}", userId, e);
            chatPushService.pushError(userId, "服务繁忙，请稍后重试");
            future.completeExceptionally(e);
        }

        return future;
    }

    private Flux<String> buildReplyChain(String userId,
                                         String userInput,
                                         String imageUrl,
                                         Boolean enableAudio,
                                         CompletableFuture<String> imageDescFuture,
                                         LlmResponseStreamParser.ParsedResult result,
                                         CompletableFuture<Void> future,
                                         AtomicReference<SpeechSynthesizer> synthesizerRef,
                                         AudioBuffer audioBuffer,
                                         List<String> replyChunks,
                                         AtomicReference<Boolean> firstChunkSent) {
        return result.getReplyStream()
                .doOnNext(chunk -> {
                    if (!firstChunkSent.get()) {
                        firstChunkSent.set(true);
                        semanticPetEventAdapter.pushChatPhase(userId, SemanticPetEventAdapter.ChatPhase.SPEAKING);
                    }
                    chatPushService.pushText(userId, chunk, false);
                    replyChunks.add(chunk);
                })
                .doOnComplete(() -> handleReplyComplete(
                        userId, userInput, imageUrl, enableAudio, imageDescFuture, replyChunks,
                        synthesizerRef, audioBuffer, future))
                .doOnError(error -> handleReplyError(userId, error, future));
    }

    private void handleReplyError(String userId, Throwable error, CompletableFuture<Void> future) {
        log.error("LLM 回复流错误：userId={}", userId, error);
        chatPushService.pushError(userId, "回复生成失败，请稍后重试");
        future.completeExceptionally(error);
    }

    private void handleReplyComplete(String userId,
                                     String userInput,
                                     String imageUrl,
                                     Boolean enableAudio,
                                     CompletableFuture<String> imageDescFuture,
                                     List<String> replyChunks,
                                     AtomicReference<SpeechSynthesizer> synthesizerRef,
                                     AudioBuffer audioBuffer,
                                     CompletableFuture<Void> future) {
        log.info("LLM 回复完成：userId={}", userId);
        String fullReply = String.join("", replyChunks);
        log.info("完整 reply: userId={}, length={}", userId, fullReply.length());

        String ttsText = fullReply.replaceAll("[（(\\[【][^）)\\]】]*[）)\\]】]", "").trim();
        if (log.isDebugEnabled()) {
            log.debug("TTS 文本(已过滤括号内容): userId={}, originalLen={}, filteredLen={}",
                    userId, fullReply.length(), ttsText.length());
        }

        chatPushService.pushText(userId, "", true);
        CompletableFuture.runAsync(() -> chatReplyPersistenceService.saveChatMemory(userId, userInput, imageUrl, imageDescFuture, fullReply), asyncExecutor);

        SpeechSynthesizer synthesizer = synthesizerRef.get();
        log.info("=== TTS 调试 === userId={}, enableAudio={}, synthesizer={}", userId, enableAudio, synthesizer);
        if (synthesizer != null && Boolean.TRUE.equals(enableAudio)) {
            chatAudioPlaybackService.startTtsPlayback(userId, fullReply, ttsText, synthesizer, audioBuffer, future);
        } else {
            future.complete(null);
        }
    }

    private void subscribeVoiceParams(String userId,
                                      Boolean enableAudio,
                                      LlmResponseStreamParser.ParsedResult result,
                                      AtomicReference<SpeechSynthesizer> synthesizerRef,
                                      AudioBuffer audioBuffer,
                                      Flux<String> replyChain) {
        result.getVoiceParams()
                .doOnSuccess(params -> {
                    log.info("voice_params 已解析：userId={}, volume={}", userId, params.getVolume());

                    SpeechSynthesizer synthesizer = chatAudioPlaybackService.initializeSynthesizer(
                            userId,
                            enableAudio,
                            params,
                            audioBuffer
                    );
                    if (synthesizer != null) {
                        synthesizerRef.set(synthesizer);
                    }
                })
                .doOnError(error -> {
                    log.error("voice_params 解析失败：userId={}", userId, error);
                    chatPushService.pushError(userId, "响应解析失败");
                })
                .doFinally(signal -> {
                    log.info("voiceParams 已完成({})，开始订阅 replyStream: userId={}", signal, userId);
                    replyChain.subscribe();
                })
                .subscribe();
    }

    private void subscribeDeltaEmotion(String userId, LlmResponseStreamParser.ParsedResult result) {
        result.getDeltaEmotion()
                .doOnSuccess(delta -> {
                    EmotionalState oldEmotion = emotionService.getUserEmotion(userId);
                    EmotionalState newEmotion = emotionService.updateUserEmotion(userId, delta);
                    log.debug("用户情绪已更新：userId={}, P={}, A={}, D={}",
                            userId, newEmotion.getPleasure(), newEmotion.getArousal(), newEmotion.getDominance());
                    anchorService.onEmotionChange(userId, oldEmotion, newEmotion);

                    String moodLabel = MoodDescriptionGenerator.generateMoodLabel(newEmotion);
                    String moodDesc = MoodDescriptionGenerator.generateMoodDescription(newEmotion);
                    chatPushService.pushEmotionUpdate(userId,
                            newEmotion.getFormattedPleasure(),
                            newEmotion.getFormattedArousal(),
                            newEmotion.getFormattedDominance(),
                            moodLabel, moodDesc);
                    semanticPetEventAdapter.pushMoodExpression(userId, moodLabel);
                })
                .doOnError(error -> log.warn("情绪更新失败：userId={}", userId, error))
                .subscribe();
    }

}
