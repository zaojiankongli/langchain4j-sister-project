package com.zjkl.emotion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmotionRecord {

    private Long id;
    private String userId;
    private BigDecimal pleasure;
    private BigDecimal arousal;
    private BigDecimal dominance;
    private String moodDescription;
    private Integer aiType;
    private LocalDateTime createdAt;
}
