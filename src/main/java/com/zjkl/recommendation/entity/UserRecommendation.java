package com.zjkl.recommendation.entity;

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
    private String description;
    private String source;             // firecrawl/context7
    private BigDecimal relevanceScore; // 0.00-1.00
    private LocalDate recommendationDate;
    private boolean isClicked;
    private LocalDateTime createdAt;
}
