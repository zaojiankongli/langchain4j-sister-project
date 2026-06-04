package com.zjkl.ai.summary.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 批量摘要评分器 — 一次 LLM 调用评多份摘要
 */
public interface ScorerAgent {

    @SystemMessage(fromResource = "prompts/summary-scoring.txt")
    @UserMessage("""
            对话原文：
            {{conversation}}

            摘要 A：
            {{summaryA}}

            摘要 B：
            {{summaryB}}

            摘要 C：
            {{summaryC}}

            请评分。
            """)
    @Agent(outputKey = "scores", description = "批量对摘要进行评分，返回 scores 数组")
    java.util.List<Integer> batchScore(@V("conversation") String conversation,
                                       @V("summaryA") String summaryA,
                                       @V("summaryB") String summaryB,
                                       @V("summaryC") String summaryC);
}
