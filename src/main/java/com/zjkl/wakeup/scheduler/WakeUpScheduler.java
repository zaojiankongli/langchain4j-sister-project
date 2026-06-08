package com.zjkl.wakeup.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.common.config.properties.WakeUpProperties;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.workflow.WakeUpWorkflow;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主动唤醒调度 — Agentic 架构：
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WakeUpScheduler {

    private static final int MAX_ACTIVE_USERS_TO_SCAN = 200;

    private final UserActivityTracker userActivityTracker;
    private final TimeContextTool timeContextTool;
    private final StringRedisTemplate redisTemplate;
    private final WakeUpWorkflow wakeUpWorkflow;

    private final WakeUpProperties wakeUpProperties;

    private static final int MAX_CONCURRENT_WAKEUPS = 4;
    private final Semaphore wakeupConcurrency = new Semaphore(MAX_CONCURRENT_WAKEUPS);

    /** 虚拟线程执行器（不限制线程创建，由 Semaphore 控制并发量） */
    private final Executor wakeupExecutor = Thread::startVirtualThread;

    @PostConstruct
    public void init() {
        log.info("唤醒执行器已初始化（虚拟线程）");
    }

    private Executor getExecutor() {
        return wakeupExecutor;
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    public void checkUsersForWakeUp() {
        if (!wakeUpProperties.isEnabled()) {
            log.debug("主动唤醒功能已禁用");
            return;
        }

        Set<String> activeUsers = userActivityTracker.getActiveMemoryIdsInLastDays(7, MAX_ACTIVE_USERS_TO_SCAN);
        if (activeUsers.isEmpty()) {
            log.debug("无活跃用户");
            return;
        }

        TimeContextTool.TimeContext timeContext = timeContextTool.getCurrentContext();
        log.info("唤醒心跳：时间={}, 时段={}, 特殊时间={}",
                timeContext.currentTime(), timeContext.timeOfDay(), timeContext.specialMoment());

        AtomicInteger passFilter = new AtomicInteger(0);
        AtomicInteger passProb = new AtomicInteger(0);
        AtomicInteger sentCount = new AtomicInteger(0);

        List<String> users = new ArrayList<>(activeUsers);
        for (int i = 0; i < users.size(); i += MAX_CONCURRENT_WAKEUPS) {
            List<String> batch = users.subList(i, Math.min(i + MAX_CONCURRENT_WAKEUPS, users.size()));
            List<CompletableFuture<Void>> futures = batch.stream()
                    .map(userId -> CompletableFuture.runAsync(() -> {
                        try {
                            int result = processUserWakeUp(userId, timeContext);
                            if (result >= 1) passFilter.incrementAndGet();
                            if (result >= 2) passProb.incrementAndGet();
                            if (result >= 3) sentCount.incrementAndGet();
                        } catch (Exception e) {
                            log.error("处理用户唤醒失败：userId={}", userId, e);
                        }
                    }, getExecutor()))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        log.info("唤醒检查完成：总用户={}, 通过过滤={}, 通过概率={}, 实际发送={}",
                activeUsers.size(), passFilter.get(), passProb.get(), sentCount.get());
    }

    private static final String PROCESSING_KEY_PREFIX = "wakeup:processing:";
    private static final long PROCESSING_KEY_TTL_SECONDS = 600;

    /**
     * 核心流程：3 并行生成 → 过滤 → 并行评分 → 仲裁 → A/B → 发送
     */
    private int processUserWakeUp(String userId, TimeContextTool.TimeContext timeContext) {
        boolean semAcquired = wakeupConcurrency.tryAcquire();
        if (!semAcquired) {
            log.debug("唤醒并发达到上限（{}），跳过：userId={}", MAX_CONCURRENT_WAKEUPS, userId);
            return 0;
        }

        // === 0.1 Redis 用户级去重（Semaphore 通过后再 SETNX，避免浪费） ===
        String processingKey = PROCESSING_KEY_PREFIX + userId;
        Boolean alreadyProcessing = redisTemplate.opsForValue().setIfAbsent(processingKey, "1",
                java.time.Duration.ofSeconds(PROCESSING_KEY_TTL_SECONDS));
        if (Boolean.FALSE.equals(alreadyProcessing)) {
            wakeupConcurrency.release();
            log.debug("用户正在被其他线程处理中，跳过：userId={}", userId);
            return 0;
        }

        boolean processed = false;
        try {
            int result = wakeUpWorkflow.processUserWakeUp(userId, timeContext);
            processed = result >= 2;
            return result;
        } finally {
            // 仅在实际执行了处理流程时缩短 TTL；早退（DND/冷却等）保留完整 TTL 防止重复处理
            if (processed) {
                try {
                    redisTemplate.expire(processingKey, 60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("缩短 processing key TTL 失败: userId={}", userId, e);
                }
            }
            if (semAcquired) {
                wakeupConcurrency.release();
            }
        }
    }

    /**
     * 手动唤醒
     */
    public void triggerWakeUpCheck(String userId) {
        log.info("手动触发唤醒检查：userId={}", userId);
        var timeContext = timeContextTool.getCurrentContext();
        processUserWakeUp(userId, timeContext);
    }
}
