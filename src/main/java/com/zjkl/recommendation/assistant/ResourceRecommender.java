package com.zjkl.recommendation.assistant;

import com.zjkl.recommendation.util.RecommendationConstants;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI Agent: 根据用户画像调用 MCP 工具搜索相关资源
 * 配备 Firecrawl + Context7 MCP 工具，自主决定搜索策略
 */
public interface ResourceRecommender {

    @SystemMessage(fromResource = "prompts/recommendation-search-prompt.txt")
    @UserMessage("""

            用户画像:
            {{userContext}}

            已有达标推荐（请勿重复这些 URL，如果为空数组则忽略此项）:
            {{passingRecommendations}}

            上一轮评分改进建议（如果为空则忽略此项）:
            {{searchFeedback}}

            请根据用户画像搜索并推荐相关资源。
            """)
    @Agent(value = "根据用户画像搜索资源，返回 JSON 格式推荐列表", outputKey = RecommendationConstants.OUTPUT_KEY_RAW_RECOMMENDATIONS)
    String recommend(@V("userContext") String userContext,
                     @V("passingRecommendations") String existingResults,
                     @V("searchFeedback") String feedback);
}
