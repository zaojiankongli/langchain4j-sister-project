package com.zjkl.user.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.user.service.InterestTagGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用户兴趣标签生成定时任务
 * 每天凌晨 2:00 执行标签生成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestTagGenerateScheduler {

    private static final String INTEREST_TAG_SCHEDULER_KEY_PREFIX = "interest-tag:scheduler:";
    private static final int MAX_ACTIVE_USERS_TO_SCAN = 200;
    /**
     * 限制并发 AI 调用数，防止 DashScope 速率限制（200 用户同时调用会触发限流）
     */
    private static final int MAX_CONCURRENT_AI_CALLS = 10;

    private final InterestTagGenerateService interestTagGenerateService;
    private final UserActivityTracker userActivityTracker;
    private final StringRedisTemplate redisTemplate;

    /**
     * 每天凌晨 2:00 执行标签生成
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 ? * *")
    public void generateDayInterestTags() {
        generateDayInterestTagsAtDate(LocalDate.now());
    }

    void generateDayInterestTagsAtDate(LocalDate currentDate) {
        log.info("========== 开始每天AI给用户标签生成任务 ==========");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 获取近 1 天有活动的用户
            Set<String> activeUserIds = userActivityTracker.getActiveMemoryIdsInLastDays(1, MAX_ACTIVE_USERS_TO_SCAN);
            AtomicInteger successCount = new AtomicInteger(0);
            Semaphore aiSemaphore = new Semaphore(MAX_CONCURRENT_AI_CALLS);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String userId : activeUserIds) {
                futures.add(CompletableFuture.runAsync(() -> {
                    String dedupKey = INTEREST_TAG_SCHEDULER_KEY_PREFIX + currentDate + ":" + userId;
                    boolean acquired = false;
                    try {
                        Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                                dedupKey,
                                "1",
                                Duration.ofDays(1)
                        );
                        acquired = Boolean.TRUE.equals(setIfAbsent);
                        if (!acquired) {
                            return;
                        }

                        // 信号量控制并发 AI 调用数，防止 DashScope 限流
                        aiSemaphore.acquire();
                        try {
                            var tags = interestTagGenerateService.generateTags(userId);
                            if (!tags.isEmpty()) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            aiSemaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (acquired) {
                            redisTemplate.delete(dedupKey);
                        }
                        log.warn("兴趣标签生成被中断: userId={}", userId);
                    } catch (Exception e) {
                        if (acquired) {
                            redisTemplate.delete(dedupKey);
                        }
                        log.error("为用户 {} 生成兴趣标签失败", userId, e);
                    }
                }, executor));
            }

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.info("兴趣标签生成任务执行完成: 共 {} 个活跃用户, 成功生成 {} 个用户标签",
                    activeUserIds.size(), successCount.get());
            log.info("========== 每天兴趣标签生成任务结束 ==========");

        } catch (Exception e) {
            log.error("每天兴趣标签生成任务执行失败", e);
        }
    }
}
