package com.zjkl.settings.model;

import lombok.Data;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 用户全局配置
 */
@Data
public class UserSettings {

    // ========== 人格设定 ==========
    /** 预设名称（gentleAndShy / tsundere / lively / coolAndDistant / intellectual / custom） */
    @NotNull
    @Pattern(regexp = "gentleAndShy|tsundere|lively|coolAndDistant|intellectual|custom")
    private String personalityPreset = "gentleAndShy";

    // ========== OCEAN 人格模型 ==========
    /** 开放性 [-1, 1] */
    @DecimalMin("-1") @DecimalMax("1")
    private double openness = 0.0;
    /** 尽责性 [-1, 1] */
    @DecimalMin("-1") @DecimalMax("1")
    private double conscientiousness = 0.0;
    /** 外向性 [-1, 1] */
    @DecimalMin("-1") @DecimalMax("1")
    private double extraversion = 0.0;
    /** 宜人性 [-1, 1] */
    @DecimalMin("-1") @DecimalMax("1")
    private double agreeableness = 0.0;
    /** 神经质 [-1, 1] */
    @DecimalMin("-1") @DecimalMax("1")
    private double neuroticism = 0.0;

    // ========== 情绪引擎 ==========
    /** 敏感度 [0, 1] */
    @DecimalMin("0") @DecimalMax("1")
    private double sensitivity = 0.5;
    /** 衰减率 [0, 1] */
    @DecimalMin("0") @DecimalMax("1")
    private double decayRate = 0.1;
    /** 回归率 [0, 1] */
    @DecimalMin("0") @DecimalMax("1")
    private double regressionRate = 0.05;

    // ========== 语音 / TTS ==========
    /** TTS 开关 */
    private boolean ttsEnabled = true;
    /** 音量 [0, 1] */
    @DecimalMin("0") @DecimalMax("1")
    private double ttsVolume = 1.0;
    /** 语速 [0.5, 2.0] */
    @DecimalMin("0.5") @DecimalMax("2.0")
    private double ttsSpeed = 1.0;

    // ========== 主动推送 ==========
    /** 是否开启主动消息 */
    private boolean proactiveEnabled = true;
    /** 主动推送间隔（分钟） */
    @Min(1) @Max(1440)
    private int proactiveIntervalMin = 30;

    // ========== 界面主题 ==========
    /** 主题 ID */
    private String themeId = "default";
}