package com.zjkl.ai.summary.agent;

import com.zjkl.ai.summary.domain.DailySummaryResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/**
 * 每日摘要生成工作流
 */

public interface DailySummaryWorkflow {

    /** 生成每日摘要 */
    @Agent(outputKey = "bestSummary", description = "生成每日对话摘要（多版本评分选择）")
    DailySummaryResult generateDailySummary(@V("conversation") String conversation,
                                            @V("previousSummary") String previousSummary);
}
