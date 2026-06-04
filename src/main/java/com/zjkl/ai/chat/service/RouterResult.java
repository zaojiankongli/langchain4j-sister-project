package com.zjkl.ai.chat.service;

/**
 * RAG 路由器返回结果 — 包含是否检索 + 可选的扁平过滤条件
 * <p>
 * 扁平结构让 langchain4j AiServices 生成简洁的 JSON schema，
 * 兼容 prompting 模式（默认）和 JSON schema 模式（开启后）。
 */
public record RouterResult(
    boolean needMemorySearch,
    boolean needGraphSearch,
    String primarySource,
    String dateHint,
    String topicHint,
    String sentimentHint
) {
    public static RouterResult noSearch() {
        return new RouterResult(false, false, null, null, null, null);
    }

    public boolean needSearch() {
        return needMemorySearch || needGraphSearch;
    }

    public MemorySearchFilters toFilters() {
        return new MemorySearchFilters(dateHint, topicHint, sentimentHint);
    }

    public boolean hasFilters() {
        return dateHint != null || topicHint != null || sentimentHint != null;
    }
}
