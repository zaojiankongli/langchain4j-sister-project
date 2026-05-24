package com.zjkl.emotion.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Personality {

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
}
