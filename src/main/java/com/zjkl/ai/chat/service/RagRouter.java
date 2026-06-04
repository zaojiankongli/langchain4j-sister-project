package com.zjkl.ai.chat.service;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RAG 路由判断服务
 * <p>
 * 使用 qwen-flash 模型 + langchain4j AiServices POJO 自动 JSON 解析，
 * 一次 LLM 调用同时输出：是否需要检索 + 可选过滤条件（日期/主题/情感）。
 */
@Slf4j
@Service
public class RagRouter {

    private static final String CONTEXT_HEADER =
            "\n\n---\n最近对话记录（用于判断是否需要搜历史记忆）：\n";
    private static final String CURRENT_MSG_PREFIX =
            "\n---\n当前用户消息：";

    private static final int MAX_CONTEXT_MESSAGES = 30;

    private final QwenChatModel qwenChatModel;
    private final QueryAnalyzer queryAnalyzer;

    public RagRouter(QwenChatModel qwenChatModel) {
        this.qwenChatModel = qwenChatModel;
        this.queryAnalyzer = AiServices.create(QueryAnalyzer.class, qwenChatModel);
    }

    /**
     * 分析用户消息，返回检索决策 + 可选过滤条件
     */
    public RouterResult analyzeQuery(String currentMessage, java.util.List<String> recentMessages) {
        if (currentMessage == null || currentMessage.isBlank()) {
            return RouterResult.noSearch();
        }
        if (currentMessage.length() < 2) {
            return RouterResult.noSearch();
        }

        // 拼接上下文
        StringBuilder ctx = new StringBuilder();
        if (recentMessages != null && !recentMessages.isEmpty()) {
            ctx.append(CONTEXT_HEADER);
            int count = 0;
            for (String msg : recentMessages) {
                if (count >= MAX_CONTEXT_MESSAGES) break;
                if (msg == null || msg.isBlank()) continue;
                ctx.append(msg).append("\n");
                count++;
            }
        }
        ctx.append(CURRENT_MSG_PREFIX).append(currentMessage);

        try {
            RouterResult result = queryAnalyzer.analyze(ctx.toString());

            String snippet = currentMessage.length() > 30
                    ? currentMessage.substring(0, 30) + "..." : currentMessage;
            log.debug("RAG 路由: \"{}\" → memory={}, graph={}, primary={}, filters=[{},{},{}]",
                    snippet, result.needMemorySearch(), result.needGraphSearch(), result.primarySource(),
                    result.dateHint(), result.topicHint(), result.sentimentHint());

            if (result.needSearch()) {
                log.info("RAG 路由 → memory={}, graph={}, primary={} for \"{}\"",
                        result.needMemorySearch(), result.needGraphSearch(), result.primarySource(), snippet);
            }
            return result;

        } catch (Exception e) {
            log.warn("RAG 路由 LLM 调用失败，使用关键词回退: {}", e.getMessage());
            return keywordFallbackRoute(currentMessage);
        }
    }

    /**
     * @deprecated 使用 {@link #analyzeQuery(String, java.util.List)}
     */
    @Deprecated
    public boolean shouldSearchMemories(String currentMessage, java.util.List<String> recentMessages) {
        return analyzeQuery(currentMessage, recentMessages).needSearch();
    }

    // ==================== 关键词回退路由 ====================

    private static final java.util.List<String> MEMORY_KEYWORDS = java.util.List.of(
            "上次", "之前", "以前", "还记得", "那天", "那次", "那时候",
            "昨天", "上周", "上个月", "说过", "聊过", "提过", "记得我"
    );
    private static final java.util.List<String> GRAPH_KEYWORDS = java.util.List.of(
            "谁", "什么人", "关系", "怎么回事", "为什么", "发生了什么",
            "怎么样", "变化", "原因"
    );

    /**
     * LLM 路由失败时的关键词回退策略
     */
    private RouterResult keywordFallbackRoute(String message) {
        boolean memory = MEMORY_KEYWORDS.stream().anyMatch(message::contains);
        boolean graph = GRAPH_KEYWORDS.stream().anyMatch(message::contains);
        if (!memory && !graph) {
            return RouterResult.noSearch();
        }
        String primary = memory && graph ? "both" : memory ? "memory" : "graph";
        return new RouterResult(memory, graph, primary, null, null, null);
    }

    // ==================== 仅保留测试用的解析方法 ====================

    /** @deprecated 仅测试用；AiServices 已自动处理 JSON → RouterResult */
    @Deprecated
    static boolean parseNeedSearch(String response) {
        if (response == null) return false;
        String cleaned = response
                .replaceAll("```[a-z]*\\s*", "").replace("```", "")
                .replaceAll("\"\\s*:\\s*\"", "\":\"").trim();
        return cleaned.matches("(?s).*\"needSearch\"\\s*:\\s*[Tt][Rr][Uu][Ee].*");
    }
}
