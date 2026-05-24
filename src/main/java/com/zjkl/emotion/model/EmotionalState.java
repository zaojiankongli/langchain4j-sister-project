package com.zjkl.emotion.model;

/**
 * 情绪状态实体类
 */
public final class EmotionalState {

    private final double pleasure;
    private final double arousal;
    private final double dominance;

    public EmotionalState() {
        this(0.0, 0.0, 0.0);
    }

    public EmotionalState(double pleasure, double arousal, double dominance) {
        this.pleasure = clamp(pleasure, -1.0, 1.0);
        this.arousal = clamp(arousal, -1.0, 1.0);
        this.dominance = clamp(dominance, -1.0, 1.0);
    }

    public double getPleasure() { return pleasure; }
    public double getArousal() { return arousal; }
    public double getDominance() { return dominance; }

    /**
     * 生成 clamp 后的新 EmotionalState 实例
     */
    public EmotionalState clamped() {
        return new EmotionalState(pleasure, arousal, dominance);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 复制当前情绪状态
     */
    public EmotionalState copy() {
        return new EmotionalState(this.pleasure, this.arousal, this.dominance);
    }

    /**
     * 判断是否为默认/中性情绪
     */
    public boolean isNeutral(double threshold) {
        return Math.abs(pleasure) < threshold &&
               Math.abs(arousal) < threshold &&
               Math.abs(dominance) < threshold;
    }

    public static double formatPadValue(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public double getFormattedPleasure() {
        return formatPadValue(this.pleasure);
    }

    public double getFormattedArousal() {
        return formatPadValue(this.arousal);
    }

    public double getFormattedDominance() {
        return formatPadValue(this.dominance);
    }
}
