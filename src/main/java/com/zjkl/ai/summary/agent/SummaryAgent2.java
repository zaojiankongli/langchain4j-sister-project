package com.zjkl.ai.summary.agent;

import com.zjkl.ai.summary.domain.DailySummaryResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummaryAgent2 {
    
    @UserMessage(fromResource = "prompts/summary-generation.txt")
    @Agent(outputKey = "summary_v2", description = "生成每日对话摘要（版本 2 - 平衡型）")
    DailySummaryResult generateSummary(@V("conversation") String conversation,
                                       @V("previousSummary") String previousSummary);
}
