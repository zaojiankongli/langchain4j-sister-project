package com.zjkl.recommendation.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户资源推荐实体
 */
@Data
public class UserRecommendation {
    private Long id;
    private String userId;
    private String resourceType;      // document/video/article
    private String title;
    private String url;
    private String imageUrl;           // 推荐资源配图（OG:image / 视频封面等）
    private String description;
    private String source;             // firecrawl/context7
    private BigDecimal relevanceScore; // 0.00-1.00
    private LocalDate recommendationDate;
    @JsonProperty("isClicked")
    private boolean isClicked;
    private LocalDateTime createdAt;
}
