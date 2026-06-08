package com.zjkl.emotion.service;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.emotion.model.VoiceParams;
import com.zjkl.emotion.util.AudioBuffer;
import com.zjkl.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatAudioPlaybackService {

    private final TtsStreamingService ttsStreamingService;
    private final ChatPushService chatPushService;
    private final SettingsService settingsService;
    @Qualifier("ttsTaskExecutor")
    private final Executor ttsTaskExecutor;

    public SpeechSynthesizer initializeSynthesizer(String userId,
                                                   Boolean enableAudio,
                                                   VoiceParams params,
                                                   AudioBuffer audioBuffer) {
        if (!Boolean.TRUE.equals(enableAudio)) {
            return null;
        }

        var settings = settingsService.getSettings(userId);
        if (!settings.isTtsEnabled()) {
            log.info("用户已关闭 TTS，跳过语音合成：userId={}", userId);
            return null;
        }

        int adjustedVolume = (int) Math.round(params.getVolume() * settings.getTtsVolume());
        adjustedVolume = Math.max(0, Math.min(100, adjustedVolume));
        VoiceParams adjustedParams = new VoiceParams(
                adjustedVolume,
                (float) settings.getTtsSpeed(),
                params.getPitchRate(),
                params.getInstruction()
        );
        SpeechSynthesizer synthesizer = ttsStreamingService.initTtsSynthesizer(userId, adjustedParams, audioBuffer);
        if (synthesizer != null) {
            log.info("TTS 已就绪（应用用户设置）：userId={}, adjustedVolume={}, adjustedSpeed={}",
                    userId, adjustedVolume, settings.getTtsSpeed());
        } else {
            log.warn("TTS 初始化返回 null，语音合成将跳过：userId={}", userId);
            chatPushService.pushError(userId, "语音服务初始化失败，已跳过音频");
        }
        return synthesizer;
    }

    public void startTtsPlayback(String userId,
                                 String fullReply,
                                 String ttsText,
                                 SpeechSynthesizer synthesizer,
                                 AudioBuffer audioBuffer,
                                 CompletableFuture<Void> future) {
        log.info("开始 TTS 流式合成：userId={}, textLength={}", userId, fullReply.length());
        AtomicReference<Throwable> ttsError = new AtomicReference<>();

        CompletableFuture.runAsync(() -> runTtsSynthesis(userId, ttsText, synthesizer, audioBuffer, ttsError), ttsTaskExecutor)
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(ex -> handleTtsTimeout(userId, synthesizer, audioBuffer, ttsError, ex));

        CompletableFuture.runAsync(() -> drainAudioBuffer(userId, audioBuffer, ttsError, future), ttsTaskExecutor)
                .orTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(ex -> handlePlaybackTimeout(userId, future, ex));
    }

    private void runTtsSynthesis(String userId,
                                 String ttsText,
                                 SpeechSynthesizer synthesizer,
                                 AudioBuffer audioBuffer,
                                 AtomicReference<Throwable> ttsError) {
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
    }

    private Void handleTtsTimeout(String userId,
                                  SpeechSynthesizer synthesizer,
                                  AudioBuffer audioBuffer,
                                  AtomicReference<Throwable> ttsError,
                                  Throwable ex) {
        log.error("TTS 合成超时或失败：userId={}", userId, ex);
        ttsError.set(ex instanceof TimeoutException ? new TimeoutException("语音合成超时（30s）") : ex);
        audioBuffer.markSynthesisCompleted();
        chatPushService.pushError(userId, "语音合成超时，已跳过音频");
        ttsStreamingService.closeSynthesizer(synthesizer);
        return null;
    }

    private void drainAudioBuffer(String userId,
                                  AudioBuffer audioBuffer,
                                  AtomicReference<Throwable> ttsError,
                                  CompletableFuture<Void> future) {
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
    }

    private Void handlePlaybackTimeout(String userId, CompletableFuture<Void> future, Throwable ex) {
        log.error("音频播放超时或失败：userId={}", userId, ex);
        future.completeExceptionally(ex instanceof TimeoutException
                ? new TimeoutException("音频播放超时（60s）") : ex);
        return null;
    }
}
