package com.zjkl.recommendation.service;

import com.zjkl.recommendation.entity.UserRecommendation;
import com.zjkl.recommendation.mapper.UserRecommendationMapper;
import com.zjkl.recommendation.util.RecommendationConstants;
import com.zjkl.recommendation.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.UntypedAgent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 资源推荐服务
 * 通过 Agentic 工作流生成推荐，解析 JSON 结果并入库
 */
@Slf4j
@Service
public class RecommendationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String GENERATION_LOCK_KEY_PREFIX = "recommendation:generate:";
    private final ExecutorService workflowExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore workflowConcurrency = new Semaphore(RecommendationConstants.SCHEDULER_MAX_CONCURRENT);

    private final UntypedAgent recommendationWorkflow;
    private final UserRecommendationMapper recommendationMapper;
    private final StringRedisTemplate redisTemplate;

    @Lazy
    @Autowired
    private RecommendationService self;

    public RecommendationService(
            @Qualifier("recommendationWorkflow") UntypedAgent recommendationWorkflow,
            UserRecommendationMapper recommendationMapper,
            StringRedisTemplate redisTemplate) {
        this.recommendationWorkflow = recommendationWorkflow;
        this.recommendationMapper = recommendationMapper;
        this.redisTemplate = redisTemplate;
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭推荐工作流线程池...");
        workflowExecutor.shutdown();
        try {
            if (!workflowExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workflowExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workflowExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 为单个用户生成推荐
     */
    public List<UserRecommendation> generateRecommendations(String userId) {
        // 检查今日是否已有推荐数据，避免重复生成
        List<UserRecommendation> existing = getTodayRecommendations(userId);
        if (!existing.isEmpty()) {
            log.info("用户 {} 今日已有 {} 条推荐，跳过生成", userId, existing.size());
            return existing;
        }

        String lockKey = GENERATION_LOCK_KEY_PREFIX + LocalDate.now() + ":" + userId;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "1",
                Duration.ofSeconds(RecommendationConstants.WORKFLOW_TIMEOUT_SECONDS + 120L)
        );
        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.info("用户 {} 的推荐生成已在进行中，跳过重复生成", userId);
            return getTodayRecommendations(userId);
        }

        boolean permitAcquired = false;
        boolean workflowRan = false;

        try {
            permitAcquired = workflowConcurrency.tryAcquire(5, TimeUnit.SECONDS);
            if (!permitAcquired) {
                log.warn("推荐生成并发已满，暂不为用户 {} 启动新工作流", userId);
                return getTodayRecommendations(userId);
            }

            log.info("为用户 {} 启动 Agentic 推荐工作流", userId);

            List<UserRecommendation> recommendations = new ArrayList<>();

            String result;
            Future<String> future = workflowExecutor.submit(() ->
                    (String) recommendationWorkflow.invoke(Map.of(
                            "userId", userId,
                            RecommendationConstants.OUTPUT_KEY_PASSING_RECOMMENDATIONS, "[]",
                            RecommendationConstants.OUTPUT_KEY_SEARCH_FEEDBACK, ""
                    )));

            try {
                result = future.get(RecommendationConstants.WORKFLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                workflowRan = true;
            } catch (TimeoutException e) {
                future.cancel(true);
                workflowRan = true;
                log.error("用户 {} 推荐工作流超时 ({}s)", userId, RecommendationConstants.WORKFLOW_TIMEOUT_SECONDS);
                throw new RuntimeException("推荐工作流超时: userId=" + userId, e);
            } catch (Exception e) {
                workflowRan = true;
                log.error("用户 {} 推荐工作流执行失败", userId, e);
                throw new RuntimeException("推荐工作流执行失败: userId=" + userId, e);
            }

            if (result == null || result.isBlank()) {
                log.warn("用户 {} 工作流返回空结果", userId);
                return recommendations;
            }

            String cleanJson = JsonUtils.stripMarkdownJson(result);

            List<UserRecommendation> parsed = parseAndSort(cleanJson, RecommendationConstants.TOP_N);
            recommendations.addAll(parsed);

            if (!recommendations.isEmpty()) {
                self.batchInsertRecommendations(userId, recommendations);
                log.info("为用户 {} 生成了 {} 条推荐（工作流返回 {} 条原始结果）",
                        userId, recommendations.size(), parsed.size());
            } else {
                log.info("用户 {} 无达标推荐结果", userId);
            }

            return recommendations;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("推荐生成等待并发许可时被中断：userId={}", userId);
            return getTodayRecommendations(userId);
        } finally {
            if (permitAcquired) {
                workflowConcurrency.release();
            }
            // Only delete the lock when the workflow did NOT run (early returns).
            // When the workflow ran and failed, let the TTL act as a cooldown
            // to prevent rapid retries from hitting the same error.
            if (!workflowRan) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchInsertRecommendations(String userId, List<UserRecommendation> recommendations) {
        LocalDate today = LocalDate.now();
        // 事务内：先清除该用户今日旧数据，再插入新数据，防止手动触发重复
        recommendationMapper.deleteByUserAndDate(userId, today);

        // 复制列表避免修改传入参数
        List<UserRecommendation> toInsert = new ArrayList<>(recommendations);
        for (UserRecommendation rec : toInsert) {
            rec.setUserId(userId);
            rec.setRecommendationDate(today);
            rec.setClicked(false);
        }
        recommendationMapper.batchInsert(toInsert);
    }

    private List<UserRecommendation> parseAndSort(String json, int topN) {
        List<UserRecommendation> all = parseRecommendations(json);
        sortByRelevanceScore(all);
        return truncateToTopN(all, topN);
    }

    private List<UserRecommendation> parseRecommendations(String json) {
        List<UserRecommendation> all = new ArrayList<>();
        try {
            JsonNode arr = OBJECT_MAPPER.readTree(json);
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return all;
            }

            for (JsonNode obj : arr) {
                try {
                    if (!obj.isObject()) continue;
                    UserRecommendation rec = new UserRecommendation();
                    rec.setTitle(getJsonString(obj, "title", "推荐资源"));
                    rec.setUrl(getJsonString(obj, "url", ""));
                    rec.setImageUrl(getJsonString(obj, "imageUrl", ""));
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

    private BigDecimal parseRelevanceScore(JsonNode obj) {
        JsonNode node = obj.get("relevanceScore");
        if (node != null && !node.isNull()) {
            return BigDecimal.valueOf(node.asDouble(0.5));
        }
        return BigDecimal.valueOf(0.5);
    }

    private String inferResourceType(JsonNode obj, String url) {
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
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
    }

    private List<UserRecommendation> truncateToTopN(List<UserRecommendation> list, int topN) {
        return list.size() > topN ? list.subList(0, topN) : list;
    }

    private String getJsonString(JsonNode obj, String key, String defaultValue) {
        JsonNode node = obj.get(key);
        if (node != null && !node.isNull()) {
            return node.asText();
        }
        return defaultValue;
    }

    public List<UserRecommendation> getTodayRecommendations(String userId) {
        return recommendationMapper.selectByUserIdAndDate(userId, LocalDate.now());
    }

    public UserRecommendation findById(Long id) {
        return recommendationMapper.selectById(id);
    }

    public void markAsClicked(Long recommendationId) {
        recommendationMapper.markAsClicked(recommendationId);
    }
}
