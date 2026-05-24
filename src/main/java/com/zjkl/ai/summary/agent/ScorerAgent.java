package com.zjkl.ai.summary.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;

public interface ScorerAgent {

    @SystemMessage(fromResource = "prompts/summary-scoring.txt")
    @Agent(outputKey = "score", description = "对摘要进行评分")
    Integer score(@V("summary") String summary,
                      @V("conversation") String conversation);
}
