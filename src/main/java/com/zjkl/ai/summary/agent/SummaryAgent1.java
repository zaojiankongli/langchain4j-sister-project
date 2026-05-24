package com.zjkl.ai.summary.agent;

import com.zjkl.ai.summary.domain.DailySummaryResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummaryAgent1 {
    
    @UserMessage(fromResource = "prompts/summary-generation.txt")
    @Agent(outputKey = "summary_v1", description = "生成每日对话摘要（版本 1 - 稳定型）")
    DailySummaryResult generateSummary(@V("conversation") String conversation,
                                       @V("previousSummary") String previousSummary);
}
