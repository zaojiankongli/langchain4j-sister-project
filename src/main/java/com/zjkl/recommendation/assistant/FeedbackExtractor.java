package com.zjkl.recommendation.assistant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zjkl.recommendation.util.JsonUtils;
import com.zjkl.recommendation.util.RecommendationConstants;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;

/**
 * Non-AI Agent: 从评分结果中提取改进建议
 * 零 Token 消耗，纯 Java 操作
 */
@Slf4j
public class FeedbackExtractor {

    @Agent(value = "从评分结果中提取搜索改进建议", outputKey = RecommendationConstants.OUTPUT_KEY_SEARCH_FEEDBACK)
    public String extract(@V(RecommendationConstants.OUTPUT_KEY_SCORED_RESULT) String scoredResult) {
        try {
            JsonObject scored = JsonParser.parseString(JsonUtils.stripMarkdownJson(scoredResult)).getAsJsonObject();
            if (scored.has("feedback") && !scored.get("feedback").isJsonNull()) {
                return scored.get("feedback").getAsString();
            }
        } catch (Exception e) {
            log.error("解析反馈提取结果失败", e);
        }
        return "质量达标，无需改进";
    }
}
