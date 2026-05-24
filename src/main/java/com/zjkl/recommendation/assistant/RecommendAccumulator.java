package com.zjkl.recommendation.assistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zjkl.recommendation.util.JsonUtils;
import com.zjkl.recommendation.util.RecommendationConstants;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * Non-AI Agent: 筛选达标推荐项、URL 去重、跨轮次累积
 * 零 Token 消耗，纯 Java 操作
 */
@Slf4j
public class RecommendAccumulator {

    private static final double PASS_THRESHOLD = 0.6;

    @Agent(value = "筛选达标推荐项，按 URL 去重，合并到已有结果", outputKey = RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS)
    public String accumulate(@V(RecommendationConstants.OUTPUT_KEY_SCORED_RESULT) String scoredResult,
                              @V(RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS) String existing) {
        JsonArray existingArr = JsonUtils.parseJsonArray(existing);

        try {
            JsonObject scored = JsonUtils.parseJsonObject(scoredResult);
            JsonArray scoredItems = new JsonArray();
            if (scored.has("recommendations") && scored.get("recommendations").isJsonArray()) {
                scoredItems = scored.getAsJsonArray("recommendations");
            }

            Set<String> existingUrls = new HashSet<>();
            for (JsonElement el : existingArr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("url")) {
                    existingUrls.add(obj.get("url").getAsString());
                }
            }

            int added = 0;
            for (JsonElement el : scoredItems) {
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("url") || !obj.has("relevanceScore")) {
                    continue;
                }
                String url = obj.get("url").getAsString();
                double score = obj.get("relevanceScore").getAsDouble();

                if (score >= PASS_THRESHOLD && !existingUrls.contains(url)) {
                    obj.addProperty("source", RecommendationConstants.SOURCE_AGENTIC);
                    existingArr.add(obj);
                    existingUrls.add(url);
                    added++;
                }
            }

            log.info("RecommendAccumulator 本轮: 新增 {} 条（阈值 {}），累计 {} 条",
                    added, PASS_THRESHOLD, existingArr.size());
        } catch (Exception e) {
            log.error("RecommendAccumulator 执行异常", e);
        }

        return JsonUtils.toJson(existingArr);
    }
}
