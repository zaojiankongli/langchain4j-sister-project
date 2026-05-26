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
                        chatPushService.pushError(userId, "语音合成失败");
                    }
                });

            log.info("SpeechSynthesizer 创建成功：userId={}", userId);
            return synthesizer;

        } catch (Exception e) {
            log.error("SpeechSynthesizer 初始化失败：userId={}", userId, e);
            chatPushService.pushError(userId, "语音服务初始化失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 关闭 Synthesizer
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

        return SpeechSynthesisParam.builder()
            .apiKey(aiProperties.getChatApiKey())
            .model(ttsProperties.getModel())
                .voice(ttsProperties.getVoice())
                .format(SpeechSynthesisAudioFormat.PCM_44100HZ_MONO_16BIT)
            .volume(params.getVolume())
            .speechRate(params.getSpeechRate())
            .pitchRate(params.getPitchRate())
            .instruction(params.getInstruction())
            .build();
    }
}
