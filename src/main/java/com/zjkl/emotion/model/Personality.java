package com.zjkl.emotion.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Personality {

    public static final List<String> PRESETS = List.of(
        "gentleAndShy", "tsundere", "lively", "coolAndDistant", "intellectual"
    );

    private double openness;
    private double conscientiousness;
    private double extraversion;
    private double agreeableness;
    private double neuroticism;

    private static final double P_O = 0.59, P_C = 0.19, P_E = 0.21, P_A = 0.15, P_N = -0.57;
    private static final double A_O = 0.25, A_E = 0.60, A_A = 0.17, A_N = -0.32;
    private static final double D_E = 0.40, D_A = 0.20, D_N = -0.51;

    public EmotionalState toBasePAD() {
        double pleasure = P_O * this.openness + P_C * this.conscientiousness
                        + P_E * this.extraversion + P_A * this.agreeableness + P_N * this.neuroticism;
        double arousal = A_O * this.openness + A_E * this.extraversion
                       + A_A * this.agreeableness + A_N * this.neuroticism;
        double dominance = D_E * this.extraversion + D_A * this.agreeableness + D_N * this.neuroticism;

        return new EmotionalState(pleasure, arousal, dominance);
    }

    public static Personality gentleAndShy() {
        return new Personality(0.0, 0.0, -0.5, 0.6, -0.2);
    }

    /** 傲娇 */
    public static Personality tsundere() {
        return new Personality(0.2, 0.1, 0.3, -0.3, 0.4);
    }

    /** 活泼 */
    public static Personality lively() {
        return new Personality(0.6, -0.2, 0.7, 0.3, -0.1);
    }

    /** 高冷 */
    public static Personality coolAndDistant() {
        return new Personality(-0.3, 0.5, -0.6, 0.1, -0.5);
    }

    /** 知性 */
    public static Personality intellectual() {
        return new Personality(0.7, 0.6, 0.0, 0.2, -0.3);
    }

    /**
     * 根据预设名称获取对应人格
     */
    public static Personality fromPreset(String name) {
        return switch (name) {
            case "gentleAndShy" -> gentleAndShy();
            case "tsundere" -> tsundere();
            case "lively" -> lively();
            case "coolAndDistant" -> coolAndDistant();
            case "intellectual" -> intellectual();
            default -> gentleAndShy();
        };
    }

    /**
     * 预设名称映射（前端展示用）
     */
    public static String presetDisplayName(String name) {
        return switch (name) {
            case "gentleAndShy" -> "温柔害羞";
            case "tsundere" -> "傲娇";
            case "lively" -> "活泼";
            case "coolAndDistant" -> "高冷";
            case "intellectual" -> "知性";
            default -> "自定义";
        };
    }
}
