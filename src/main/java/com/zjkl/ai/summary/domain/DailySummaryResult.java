package com.zjkl.ai.summary.domain;

/**
 * 每日摘要生成结果
 * 
 * 包含 AI 生成的标题和摘要内容。
 */
public record DailySummaryResult(
    /**
     * 摘要标题（10-20 字，概括当天对话核心主题）
     */
    String title,
    
    /**
     * 摘要正文（200 字以内，包含关键信息和情感状态）
     */
    String summary
) {
}
