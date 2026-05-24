package com.zjkl.recommendation.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zjkl.recommendation.entity.UserRecommendation;
import com.zjkl.recommendation.mapper.UserRecommendationMapper;
import com.zjkl.recommendation.util.JsonUtils;
import com.zjkl.recommendation.util.RecommendationConstants;
import dev.langchain4j.agentic.UntypedAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 资源推荐服务
 * 通过 Agentic 工作流生成推荐，解析 JSON 结果并入库
 */
@Slf4j
@Service
public class RecommendationService {

    private static final int TOP_N = 15;
    private static final int WORKFLOW_TIMEOUT_SECONDS = 300;
    private static final Gson GSON = new Gson();
    private static final ExecutorService WORKFLOW_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final UntypedAgent recommendationWorkflow;
    private final UserRecommendationMapper recommendationMapper;

    public RecommendationService(
            @Qualifier("recommendationWorkflow") UntypedAgent recommendationWorkflow,
            UserRecommendationMapper recommendationMapper) {
        this.recommendationWorkflow = recommendationWorkflow;
        this.recommendationMapper = recommendationMapper;
    }

    /**
     * 为单个用户生成推荐
     */
    public List<UserRecommendation> generateRecommendations(String userId) {
        log.info("为用户 {} 启动 Agentic 推荐工作流", userId);

        List<UserRecommendation> recommendations = new ArrayList<>();

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    (String) recommendationWorkflow.invoke(Map.of(
                            "userId", userId,
                            RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS, "[]",
                            RecommendationConstants.OUTPUT_KEY_SEARCH_FEEDBACK, ""
                    )), WORKFLOW_EXECUTOR
            );

            String result = future
                    .orTimeout(WORKFLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        future.cancel(true);
                        log.error("用户 {} 工作流执行超时或失败", userId, ex);
                        return null;
                    })
                    .join();

            if (result == null || result.isBlank()) {
                log.warn("用户 {} 工作流返回空结果", userId);
                return recommendations;
            }

            String cleanJson = JsonUtils.stripMarkdownJson(result);

            List<UserRecommendation> parsed = parseAndSort(cleanJson, TOP_N);
            recommendations.addAll(parsed);

            if (!recommendations.isEmpty()) {
                batchInsertRecommendations(userId, recommendations);
                log.info("为用户 {} 生成了 {} 条推荐（工作流返回 {} 条原始结果）",
                        userId, recommendations.size(), parsed.size());
            } else {
                log.info("用户 {} 无达标推荐结果", userId);
            }

        } catch (Exception e) {
            log.error("为用户 {} 生成推荐失败", userId, e);
        }

        return recommendations;
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchInsertRecommendations(String userId, List<UserRecommendation> recommendations) {
        LocalDate today = LocalDate.now();
        for (UserRecommendation rec : recommendations) {
            rec.setUserId(userId);
            rec.setRecommendationDate(today);
            rec.setClicked(false);
        }
        recommendationMapper.batchInsert(recommendations);
    }

    private List<UserRecommendation> parseAndSort(String json, int topN) {
        List<UserRecommendation> all = parseRecommendations(json);
        sortByRelevanceScore(all);
        return truncateToTopN(all, topN);
    }

    private List<UserRecommendation> parseRecommendations(String json) {
        List<UserRecommendation> all = new ArrayList<>();
        try {
            JsonArray arr = GSON.fromJson(json, JsonArray.class);
            if (arr == null || arr.isEmpty()) {
                return all;
            }

            for (JsonElement el : arr) {
                try {
                    JsonObject obj = el.getAsJsonObject();
                    UserRecommendation rec = new UserRecommendation();
                    rec.setTitle(getJsonString(obj, "title", "推荐资源"));
                    rec.setUrl(getJsonString(obj, "url", ""));
                    rec.setDescription(getJsonString(obj, "description", ""));
                    rec.setRelevanceScore(parseRelevanceScore(obj));
                    rec.setResourceType(inferResourceType(obj, rec.getUrl()));
                    rec.setSource(getJsonString(obj, "source", "agentic"));

                    if (rec.getUrl() != null && !rec.getUrl().isBlank()) {
                        all.add(rec);
                    }
                } catch (Exception e) {
                    log.debug("解析单条推荐失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("解析推荐 JSON 失败: {}", e.getMessage());
        }
        return all;
    }

    private BigDecimal parseRelevanceScore(JsonObject obj) {
        if (obj.has("relevanceScore") && !obj.get("relevanceScore").isJsonNull()) {
            return BigDecimal.valueOf(obj.get("relevanceScore").getAsDouble());
        }
        return BigDecimal.valueOf(0.5);
    }

    private String inferResourceType(JsonObject obj, String url) {
        String type = getJsonString(obj, "resourceType", "");
        if (!type.isEmpty()) {
            return type;
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("youtube") || lowerUrl.contains("bilibili") || lowerUrl.contains("vimeo")) {
            return "video";
        } else if (lowerUrl.contains("blog") || lowerUrl.contains("article") || lowerUrl.contains("medium")) {
            return "article";
        }
        return "document";
    }

    private void sortByRelevanceScore(List<UserRecommendation> list) {
        list.sort(Comparator.comparing(
                UserRecommendation::getRelevanceScore,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed());
    }

    private List<UserRecommendation> truncateToTopN(List<UserRecommendation> list, int topN) {
        return list.size() > topN ? list.subList(0, topN) : list;
    }

    private String getJsonString(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    public List<UserRecommendation> getTodayRecommendations(String userId) {
        return recommendationMapper.selectByUserIdAndDate(userId, LocalDate.now());
    }

    public void markAsClicked(Long recommendationId) {
        recommendationMapper.markAsClicked(recommendationId);
    }
}
