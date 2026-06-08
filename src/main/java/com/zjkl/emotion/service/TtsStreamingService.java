package com.zjkl.emotion.service;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.zjkl.common.config.properties.AiProperties;
import com.zjkl.common.config.properties.TtsProperties;
import com.zjkl.emotion.model.VoiceParams;
import com.zjkl.emotion.util.AudioBuffer;
import com.zjkl.ai.chat.stomp.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsStreamingService {

    private static final int MAX_TTS_INSTRUCTION_CHARS = 120;

    private final AiProperties aiProperties;
    private final TtsProperties ttsProperties;
    private final ChatPushService chatPushService;

    /**
     * 初始化 TTS
     */
    public SpeechSynthesizer initTtsSynthesizer(String userId, VoiceParams params, AudioBuffer audioBuffer) {
        try {
            SpeechSynthesisParam synthesisParam = buildDashScopeParam(params);

            log.info("开始创建 SpeechSynthesizer：userId={}", userId);

            SpeechSynthesizer synthesizer = new SpeechSynthesizer(synthesisParam,
                new ResultCallback<SpeechSynthesisResult>() {
                    @Override
                    public void onEvent(SpeechSynthesisResult result) {
                        log.debug("TTS onEvent 回调触发：userId={}, hasAudio={}", userId, result.getAudioFrame() != null);

                        if (result.getAudioFrame() != null && result.getAudioFrame().hasRemaining()) {

                            ByteBuffer byteBuffer = result.getAudioFrame();
                            int available = byteBuffer.remaining();
                            byte[] audioData = new byte[available];
                            byteBuffer.get(audioData);

                            // 写入音频缓冲区
                            audioBuffer.addAudio(audioData);
                            log.debug("音频写入缓冲区：userId={}, size={}, 累计={}ms",
                                    userId, audioData.length, audioBuffer.estimateDurationMs());
                        }
                    }

                    @Override
                    public void onComplete() {
                        log.info("TTS 合成完成：userId={}", userId);
                        audioBuffer.markSynthesisCompleted();
                    }

                    @Override
                    public void onError(Exception e) {
                        log.error("TTS 合成错误：userId={}", userId, e);
                        audioBuffer.markSynthesisCompleted();
                        chatPushService.pushError(userId, "语音合成失败");
                    }
                });

            log.info("SpeechSynthesizer 创建成功：userId={}", userId);
            return synthesizer;

        } catch (Exception e) {
            log.error("SpeechSynthesizer 初始化失败：userId={}", userId, e);
            chatPushService.pushError(userId, "语音服务初始化失败，请稍后重试");
            throw new RuntimeException("语音合成初始化失败", e);
        }
    }

    /**
     * 关闭 Synthesizer（安全关闭，catch 异常不抛出）
     * 每个 synthesizer 独立管理生命周期，不使用共享标记
     */
    public void closeSynthesizer(SpeechSynthesizer synthesizer) {
        if (synthesizer == null) return;
        try {
            var duplexApi = synthesizer.getDuplexApi();
            if (duplexApi != null) {
                duplexApi.close(1000, "bye");
            }
        } catch (Exception closeEx) {
            log.warn("关闭 SpeechSynthesizer 失败", closeEx);
        }
    }

    /**
     * 构建 DashScope 参数
     */
    private SpeechSynthesisParam buildDashScopeParam(VoiceParams params) {
        String instruction = sanitizeInstruction(params.getInstruction());

        return SpeechSynthesisParam.builder()
            .apiKey(aiProperties.getChatApiKey())
            .model(ttsProperties.getModel())
                .voice(ttsProperties.getVoice())
                .format(SpeechSynthesisAudioFormat.PCM_44100HZ_MONO_16BIT)
            .volume(params.getVolume())
            .speechRate(params.getSpeechRate())
            .pitchRate(params.getPitchRate())
            .instruction(instruction)
            .build();
    }

    private String sanitizeInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        String trimmed = instruction.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= MAX_TTS_INSTRUCTION_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_TTS_INSTRUCTION_CHARS);
    }
}
