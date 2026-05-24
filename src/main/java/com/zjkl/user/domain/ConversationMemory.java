package com.zjkl.user.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对话记忆实体类
 */
@Data
@Builder
public class ConversationMemory {
    
    /**
     * 主键 ID
     */
    private Long id;
    
    /**
     * 用户 ID
     */
    private String userId;
    
    /**
     * 标题（主题词）
     */
    private String title;
    
    /**
     * 内容（对话摘要/日记）
     */
    private String content;

    /**
     * 心情标签
     */
    private String mood;

    /**
     * 记忆日期（只有年月日）
     */
    private LocalDate memoryDate;
    
    /**
     * 关联的图片 URL
     */
    private String imageUrl;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
