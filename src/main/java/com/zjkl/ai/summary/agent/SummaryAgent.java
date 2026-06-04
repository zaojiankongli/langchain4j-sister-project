package com.zjkl.ai.summary.agent;

import com.zjkl.ai.summary.domain.DailySummaryResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 每日摘要生成 Agent（多版本评分选择）
 * 由 AgentConfig 创建多个 bean 实例，使用相同 prompt 但不同 outputKey
 */
public interface SummaryAgent {

    @UserMessage(fromResource = "prompts/summary-generation.txt")
    @Agent(outputKey = "summary", description = "生成每日对话摘要")
    DailySummaryResult generateSummary(@V("conversation") String conversation,
                                       @V("previousSummary") String previousSummary);
}
