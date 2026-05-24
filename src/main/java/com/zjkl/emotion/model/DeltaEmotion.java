package com.zjkl.emotion.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeltaEmotion {

    private Double deltaP;
    private Double deltaA;
    private Double deltaD;

    private static final double POSITIVE_P = 0.3, POSITIVE_A = 0.2, POSITIVE_D = 0.1;
    private static final double NEGATIVE_P = -0.3, NEGATIVE_A = -0.1, NEGATIVE_D = -0.2;
    private static final double SHY_P = 0.1, SHY_A = 0.4, SHY_D = -0.3;

    public static DeltaEmotion positive(double intensity) {
        return new DeltaEmotion(POSITIVE_P * intensity, POSITIVE_A * intensity, POSITIVE_D * intensity);
    }

    public static DeltaEmotion negative(double intensity) {
        return new DeltaEmotion(NEGATIVE_P * intensity, NEGATIVE_A * intensity, NEGATIVE_D * intensity);
    }

    public static DeltaEmotion shy(double intensity) {
        return new DeltaEmotion(SHY_P * intensity, SHY_A * intensity, SHY_D * intensity);
    }

    public DeltaEmotion clamp() {
        this.deltaP = clamp(this.deltaP);
        this.deltaA = clamp(this.deltaA);
        this.deltaD = clamp(this.deltaD);
        return this;
    }

    private static Double clamp(Double value) {
        if (value == null) return 0.0;
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
