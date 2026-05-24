package com.zjkl.ai.summary.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 图片生成任务
 * 
 * 用于 Redis Stream 消息传递，由摘要消费者发送给图片消费者
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageGenerationTask {
    
    /**
     * 任务 ID（UUID）
     * 用于追踪和日志记录
     */
    private String taskId;
    
    /**
     * 用户 ID
     */
    private String userId;
    
    /**
     * 摘要标题（由 LLM 生成）
     */
    private String title;
    
    /**
     * 摘要正文（由 LLM 生成）
     */
    private String summary;
    
    /**
     * 记忆日期
     */
    private LocalDate memoryDate;
    
    /**
     * 任务创建时间（保持与原始触发时间一致）
     */
    private LocalDateTime createdAt;
}
