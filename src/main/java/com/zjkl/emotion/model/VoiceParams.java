package com.zjkl.emotion.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音参数实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoiceParams {
    
    /**
     * 音量 [0-100]
     * 0 为静音，100 为最大音量
     */
    private Integer volume;
    
    /**
     * 语速 [0.5-2.0]
     * 1.0 为标准语速
     */
    private Float speechRate;
    
    /**
     * 音高 [0.5-2.0]
     * 1.0 为自然音高
     */
    private Float pitchRate;
    
    /**
     * 情感指令 [100 字内]
     * 用于控制方言、情感、语气等合成效果
     */
    private String instruction;

    /**
     * 根据情绪状态规则推导语音参数
     */
    public static VoiceParams fromEmotion(EmotionalState state) {
        if (state == null) {
            return new VoiceParams(60, 1.0f, 1.0f, "温和地");
        }

        double p = state.getPleasure();

        if (p > 0.7) {
            return new VoiceParams(75, 1.3f, 1.3f, "开心地");
        } else if (p > 0.3) {
            return new VoiceParams(65, 1.1f, 1.1f, "温和地");
        } else if (p > -0.3) {
            return new VoiceParams(55, 1.0f, 1.0f, "平静地");
        } else {
            return new VoiceParams(50, 0.8f, 0.8f, "轻柔地");
        }
    }
}
