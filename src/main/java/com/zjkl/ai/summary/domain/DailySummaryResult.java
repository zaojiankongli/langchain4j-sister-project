package com.zjkl.ai.summary.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 每日摘要生成结果
 * 
 * 包含 AI 生成的标题、摘要内容及情感分析。
 */
public record DailySummaryResult(
    /**
     * 摘要标题（10-20 字，概括当天对话核心主题）
     */
    String title,
    
    /**
     * 摘要正文（200 字以内，包含关键信息和情感状态）
     */
    String summary,

    /**
     * 情感分数（-1.0 到 1.0，LLM 从对话内容判断）
     */
    @JsonProperty("sentiment_score")
    double sentimentScore,

    /**
     * 情感标签（简短的正面/负面/中性描述，如：开心、焦虑、平静）
     */
    @JsonProperty("emotion_label")
    String emotionLabel
) {
}
