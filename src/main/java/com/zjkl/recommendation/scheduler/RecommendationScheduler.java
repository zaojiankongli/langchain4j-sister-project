package com.zjkl.recommendation.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 资源推荐定时任务
 * 每天凌晨 1:00 执行推荐生成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final RecommendationService recommendationService;
    private final UserActivityTracker userActivityTracker;

    /**
     * 每天凌晨 1:00 执行推荐生成
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDailyRecommendations() {
        log.info("========== 开始每日资源推荐任务 ==========");

        try {
            Set<String> ids = userActivityTracker.getActiveMemoryIdsInLastDays(1);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Integer>> futures = ids.stream()
                        .map(userId -> CompletableFuture.supplyAsync(() -> {
                            try {
                                var recs = recommendationService.generateRecommendations(userId);
                                return recs.isEmpty() ? 0 : 1;
                            } catch (Exception e) {
                                log.error("为用户 {} 生成推荐失败", userId, e);
                                return 0;
                            }
                        }, executor))
                        .collect(Collectors.toList());

                long successCount = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenApply(v -> futures.stream().mapToInt(CompletableFuture::join).sum())
                        .get();

                log.info("推荐任务执行完成: 共 {} 个活跃用户, 成功生成 {} 个用户推荐", ids.size(), successCount);
            }

            log.info("========== 每日资源推荐任务结束 ==========");

        } catch (Exception e) {
            log.error("每日资源推荐任务执行失败", e);
        }
    }
}
