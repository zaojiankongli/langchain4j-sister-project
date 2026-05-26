package com.zjkl.emotion.service;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.zjkl.common.config.properties.AiProperties;
import com.zjkl.common.config.properties.TtsProperties;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.emotion.model.VoiceParams;
import com.zjkl.emotion.model.VoiceSynthesisParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

/**
 * 语音合成服务
 *
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VoiceSynthesisService {

    private final AiProperties aiProperties;
    private final TtsProperties ttsProperties;

    /**
     * 非流式语音合成
     * 
     * @param text 待合成文本
     * @param emotion 情绪状态
     * @return 音频数据 ByteBuffer
     */
    public ByteBuffer synthesize(String text, EmotionalState emotion) {
        VoiceParams vp = VoiceParams.fromEmotion(emotion);
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(aiProperties.getChatApiKey())
                .model(ttsProperties.getModel())
                .voice(ttsProperties.getVoice())
                .format(SpeechSynthesisAudioFormat.PCM_44100HZ_MONO_16BIT)
                .volume(vp.getVolume())
                .speechRate(vp.getSpeechRate())
                .pitchRate(vp.getPitchRate())
                .instruction(vp.getInstruction())
                .build();
        
        // 3. 创建合成器（非流式，callback 为 null）
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        
        try {
            // 4. 阻塞式合成，返回完整音频
            ByteBuffer audio = synthesizer.call(text);

            // 5. 记录指标
            log.info("[Metric] requestId={}, 首包延迟={}ms",
                synthesizer.getLastRequestId(), synthesizer.getFirstPackageDelay());

            return audio;
        } catch (Exception e) {
            throw new RuntimeException("语音合成失败", e);
        } finally {
            closeSynthesizer(synthesizer);
        }
    }

    /**
     * 使用自定义参数进行语音合成
     *
     * @param text 待合成文本
     * @param voiceParam 语音参数
     * @return 音频数据 ByteBuffer
     */
    public ByteBuffer synthesize(String text, VoiceSynthesisParam voiceParam) {
        SpeechSynthesisParam param = voiceParam.toDashScopeParam(aiProperties.getChatApiKey());
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);

        try {
            ByteBuffer audio = synthesizer.call(text);
            log.info("[Metric] requestId={}, 首包延迟={}ms",
                synthesizer.getLastRequestId(), synthesizer.getFirstPackageDelay());
            return audio;
        } catch (Exception e) {
            throw new RuntimeException("语音合成失败", e);
        } finally {
            closeSynthesizer(synthesizer);
        }
    }

    private void closeSynthesizer(SpeechSynthesizer synthesizer) {
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
     * 获取默认语音参数
     */
    public VoiceSynthesisParam getDefaultParam() {
        return VoiceSynthesisParam.defaults();
    }
}
