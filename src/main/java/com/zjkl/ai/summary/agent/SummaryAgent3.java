package com.zjkl.ai.summary.agent;

import com.zjkl.ai.summary.domain.DailySummaryResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummaryAgent3 {
    
    @UserMessage(fromResource = "prompts/summary-generation.txt")
    @Agent(outputKey = "summary_v3", description = "生成每日对话摘要（版本 3 - 创造型）")
    DailySummaryResult generateSummary(@V("conversation") String conversation,
                                       @V("previousSummary") String previousSummary);
}
