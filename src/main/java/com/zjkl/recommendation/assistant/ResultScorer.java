package com.zjkl.recommendation.assistant;

import com.zjkl.recommendation.util.RecommendationConstants;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI Agent: 对搜索结果逐条评分，输出带分数的推荐列表和改进建议
 */
public interface ResultScorer {

    @SystemMessage(fromResource = "prompts/recommendation-score-prompt.txt")
    @UserMessage("""

            用户画像:
            {{userContext}}

            待评分的推荐资源:
            {{rawRecommendations}}

            请逐条评估每条推荐与用户画像的相关性，给出分数和改进建议。
            """)
    @Agent(value = "评估推荐质量并逐条打分，输出评分结果和改进建议", outputKey = RecommendationConstants.OUTPUT_KEY_SCORED_RESULT)
    String score(@V("userContext") String userContext,
                 @V("rawRecommendations") String rawResults);
}
