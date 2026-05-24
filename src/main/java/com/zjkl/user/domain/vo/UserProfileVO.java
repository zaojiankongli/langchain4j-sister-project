package com.zjkl.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户资料 VO
 * 通过 @JsonProperty 映射为 snake_case，匹配前端字段命名
 */
@Data
public class UserProfileVO {
    
    // ========== 基础信息 ==========
    private String id;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("avatar_url")
    private String avatarUrl;
    
    @JsonProperty("gender")
    private Integer gender;
    
    @JsonProperty("birthday")
    private LocalDate birthday;
    
    @JsonProperty("hobbies")
    private String hobbies;
    
    @JsonProperty("user_profile")
    private String userProfile;
    
    @JsonProperty("ai_type")
    private Integer aiType;
    
    @JsonProperty("last_active_at")
    private LocalDateTime lastActiveAt;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
    
    // ========== AI 生成的标签 ==========
    @JsonProperty("interest_tags")
    private List<String> interestTags;
    
    // ========== 统计数据 ==========
    @JsonProperty("meet_days")
    private Integer meetDays;
    
    @JsonProperty("message_count")
    private Integer messageCount;
    
    @JsonProperty("first_chat_time")
    private LocalDateTime firstChatTime;
    
    // ========== 等级经验 ==========
    @JsonProperty("current_level")
    private Integer currentLevel;
    
    @JsonProperty("current_exp")
    private Integer currentExp;
    
    @JsonProperty("level_up_exp")
    private Integer levelUpExp;
    
    @JsonProperty("total_exp")
    private Integer totalExp;
    
    // ========== 情感 PAD ==========
    private Double pleasure;
    private Double arousal;
    private Double dominance;
    
    @JsonProperty("mood_description")
    private String moodDescription;
    
    /**
     * 等级信息（用于 Mapper 结果映射）
     */
    @Data
    public static class LevelInfo {
        private Integer currentLevel;
        private Integer currentExp;
        private Integer levelUpExp;
        private Integer totalExp;
    }
    
    /**
     * 情绪信息（用于 Mapper 结果映射）
     */
    @Data
    public static class EmotionInfo {
        private Double pleasure;
        private Double arousal;
        private Double dominance;
        private String moodDescription;
    }
}
