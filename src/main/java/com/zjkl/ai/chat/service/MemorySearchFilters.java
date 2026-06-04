package com.zjkl.ai.chat.service;

/**
 * RAG 混合检索过滤条件
 * 所有字段可选——为 null 表示不限制
 */
public record MemorySearchFilters(
    /** 日期提示词，如 "最近一周"、"2026年5月" — 用于 metadata.create_time 范围匹配 */
    String dateHint,
    /** 主题提示词，如 "摄影"、"工作压力" — 作为 query 的补充增强召回 */
    String topicHint,
    /** 情感提示词，如 "开心的"、"难过的" — 用于 metadata.emotion_label 匹配 */
    String sentimentHint
) {
    public static MemorySearchFilters EMPTY = new MemorySearchFilters(null, null, null);

    public boolean isEmpty() {
        return dateHint == null && topicHint == null && sentimentHint == null;
    }
}
