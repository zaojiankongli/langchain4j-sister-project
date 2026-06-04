package com.zjkl.recommendation.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.recommendation.service.RecommendationService;
import com.zjkl.recommendation.util.RecommendationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 资源推荐定时任务
 * 每天凌晨 1:00 执行推荐生成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private static final String RECOMMENDATION_SCHEDULER_KEY_PREFIX = "recommendation:scheduler:";
    private static final int MAX_ACTIVE_USERS_TO_SCAN = 200;

    private final RecommendationService recommendationService;
    private final UserActivityTracker userActivityTracker;
    private final StringRedisTemplate redisTemplate;

    /**
     * 每天凌晨 1:00 执行推荐生成
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDailyRecommendations() {
        log.info("========== 开始每日资源推荐任务 ==========");

        try {
            Set<String> ids = userActivityTracker.getActiveMemoryIdsInLastDays(1, MAX_ACTIVE_USERS_TO_SCAN);

            if (ids.isEmpty()) {
                log.info("无活跃用户，跳过推荐生成");
                log.info("========== 每日资源推荐任务结束 ==========");
                return;
            }

            List<String> users = new ArrayList<>(ids);
            long successCount = 0;

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < users.size(); i += RecommendationConstants.SCHEDULER_MAX_CONCURRENT) {
                    List<String> batch = users.subList(i,
                            Math.min(i + RecommendationConstants.SCHEDULER_MAX_CONCURRENT, users.size()));

                    List<CompletableFuture<Integer>> futures = batch.stream()
                            .map(userId -> CompletableFuture.supplyAsync(() -> {
                                String dedupKey = RECOMMENDATION_SCHEDULER_KEY_PREFIX + LocalDate.now() + ":" + userId;
                                boolean acquired = false;
                                try {
                                    Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                                            dedupKey,
                                            "1",
                                            Duration.ofDays(1)
                                    );
                                    acquired = Boolean.TRUE.equals(setIfAbsent);
                                    if (!acquired) {
                                        return 0;
                                    }

                                    var recs = recommendationService.generateRecommendations(userId);
                                    return recs.isEmpty() ? 0 : 1;
                                } catch (Exception e) {
                                    if (acquired) {
                                        redisTemplate.delete(dedupKey);
                                    }
                                    log.error("为用户 {} 生成推荐失败", userId, e);
                                    return 0;
                                }
                            }, executor))
                            .toList();

                    CompletableFuture<Void> allDone = CompletableFuture.allOf(
                            futures.toArray(new CompletableFuture[0]));
                    allDone.get(RecommendationConstants.WORKFLOW_TIMEOUT_SECONDS + 120L, TimeUnit.SECONDS);
                    successCount += futures.stream().mapToInt(CompletableFuture::join).sum();
                }

                long failCount = ids.size() - successCount;

                log.info("推荐任务执行完成: 活跃用户={}, 成功={}, 失败={}", ids.size(), successCount, failCount);
            }

            log.info("========== 每日资源推荐任务结束 ==========");

        } catch (Exception e) {
            log.error("每日资源推荐任务执行失败", e);
        }
    }
}
