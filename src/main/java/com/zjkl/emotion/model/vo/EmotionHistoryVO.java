package com.zjkl.emotion.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EmotionHistoryVO {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("pleasure")
    private BigDecimal pleasure;

    @JsonProperty("arousal")
    private BigDecimal arousal;

    @JsonProperty("dominance")
    private BigDecimal dominance;

    @JsonProperty("mood_description")
    private String moodDescription;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
