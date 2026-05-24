package com.zjkl.ai.summary.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 摘要生成任务
 * 
 * 用于 Redis Stream 消息传递，解耦摘要生成和图片生成流程
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SummaryGenerationTask {
    
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
     * 对话文本（已过滤系统消息）
     */
    private String conversationText;
    
    /**
     * 旧摘要（用于 AI 上下文）
     */
    private String previousSummary;
    
    /**
     * 任务创建时间
     */
    private LocalDateTime createdAt;
}
