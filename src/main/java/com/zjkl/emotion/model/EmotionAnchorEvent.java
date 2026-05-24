package com.zjkl.emotion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 情绪锚点事件 — 触发→监测→结束
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnchorEvent {

    private Long id;
    private String userId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSeconds;

    private BigDecimal startPleasure;
    private BigDecimal peakPleasure;
    private BigDecimal endPleasure;
    private BigDecimal deltaPleasure;

    private BigDecimal startArousal;
    private BigDecimal peakArousal;
    private BigDecimal endArousal;
    private BigDecimal deltaArousal;

    private String eventTitle;
    private String triggerReason;
    private String highlightTraits;
    private String summary;

    private EndType endType;
    private String endReason;
    private String aiReflection;
    private LocalDateTime createdAt;

    public enum EndType {
        POSITIVE("正向结束", "情绪平稳回归基准，事件自然结束"),
        NEGATIVE("负向结束", "情绪未回归基准，问题可能未解决");

        private final String description;
        private final String meaning;

        EndType(String description, String meaning) {
            this.description = description;
            this.meaning = meaning;
        }

        public String getDescription() {
            return description;
        }

        public String getMeaning() {
            return meaning;
        }
    }

    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationSeconds = (int) java.time.Duration.between(startTime, endTime).getSeconds();
        }
    }
}
