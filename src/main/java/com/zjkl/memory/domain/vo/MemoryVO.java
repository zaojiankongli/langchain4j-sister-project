package com.zjkl.memory.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 记忆片段 VO（返回给前端 MemoryFragment）
 * 通用字段 + 锚点专属字段 + 日记专属字段
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryVO {
    private Long id;

    @JsonProperty("date")
    private String date;

    @JsonProperty("quote")
    private String quote;

    @JsonProperty("desc")
    private String desc;

    @JsonProperty("mood")
    private String mood;

    /** milestone=锚点, journal=日记 */
    @JsonProperty("type")
    private String type;

    // ===== 日记专属字段 =====
    @JsonProperty("title")
    private String title;

    @JsonProperty("content")
    private String content;

    @JsonProperty("imageUrl")
    private String imageUrl;

    // ===== 锚点专属字段 =====
    @JsonProperty("eventTitle")
    private String eventTitle;

    @JsonProperty("endTime")
    private String endTime;

    @JsonProperty("durationSeconds")
    private Integer durationSeconds;

    @JsonProperty("startPleasure")
    private BigDecimal startPleasure;

    @JsonProperty("endPleasure")
    private BigDecimal endPleasure;

    @JsonProperty("peakPleasure")
    private BigDecimal peakPleasure;

    @JsonProperty("deltaPleasure")
    private BigDecimal deltaPleasure;

    @JsonProperty("startArousal")
    private BigDecimal startArousal;

    @JsonProperty("endArousal")
    private BigDecimal endArousal;

    @JsonProperty("peakArousal")
    private BigDecimal peakArousal;

    @JsonProperty("deltaArousal")
    private BigDecimal deltaArousal;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("endType")
    private String endType;

    @JsonProperty("aiReflection")
    private String aiReflection;

    @JsonProperty("highlightTraits")
    private String highlightTraits;

    @JsonProperty("triggerReason")
    private String triggerReason;

    @JsonProperty("endReason")
    private String endReason;

    @JsonProperty("startTime")
    private String startTime;
}
