package com.zjkl.peek.scheduler;

import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.peek.tool.PeekStateTool;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Peek 定时调度 — 在线+活跃用户定期截图请求
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeekScheduler {

    private final UserActivityTracker userActivityTracker;
    private final UserStateTool userStateTool;
    private final TimeContextTool timeContextTool;
    private final PeekStateTool peekStateTool;
    private final ChatPushService chatPushService;
    private final StringRedisTemplate redisTemplate;

    @Value("${peek.enabled:true}")
    private boolean peekEnabled;

    @Value("${peek.peek-request-ttl-seconds:120}")
    private int peekRequestTtlSeconds;

    @Value("${peek.max-concurrent-requests:5}")
    private int maxConcurrentRequests;

    private static final String PEEK_PENDING_KEY_PREFIX = "peek:pending:";
    private static final String PEEK_RATE_LIMIT_KEY = "peek:rate_limit:current";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(30);
    private static final Executor PEEK_EXECUTOR = Thread::startVirtualThread;

    @Scheduled(cron = "0 0/20 8-22 * * ?")
    public void checkUsersForPeek() {
        if (!peekEnabled) {
            log.debug("peek 功能已禁用");
            return;
        }

        Set<String> activeUsers = userActivityTracker.getActiveMemoryIdsInLastDays(1);
        if (activeUsers == null || activeUsers.isEmpty()) {
            log.debug("无今日活跃用户");
            return;
        }

        TimeContextTool.TimeContext timeContext = timeContextTool.getCurrentContext();
        log.info("peek 心跳：时间={}, 时段={}, 特殊时间={}, 活跃用户={}",
                timeContext.currentTime(), timeContext.timeOfDay(), timeContext.specialMoment(),
                activeUsers.size());

        AtomicInteger passFilter = new AtomicInteger(0);
        AtomicInteger requestSent = new AtomicInteger(0);

        CompletableFuture<?>[] futures = activeUsers.stream()
                .map(userId -> CompletableFuture.runAsync(() -> {
                    try {
                        int result = processUserPeek(userId, timeContext);
                        if (result >= 1) passFilter.incrementAndGet();
                        if (result >= 2) requestSent.incrementAndGet();
                    } catch (Exception e) {
                        log.error("处理用户 peek 失败：userId={}", userId, e);
                    }
                }, PEEK_EXECUTOR))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        log.info("peek 检查完成：总用户={}, 通过过滤={}, 发送请求={}",
                activeUsers.size(), passFilter.get(), requestSent.get());
    }

    /** 处理用户 peek */
    private int processUserPeek(String userId, TimeContextTool.TimeContext timeContext) {
        if (!peekStateTool.isPeekEnabled(userId)) {
            return 0;
        }

        if (!chatPushService.isUserConnected(userId)) {
            return 0;
        }

        if (!peekStateTool.isUserActive(userId)) {
            return 0;
        }

        if (userStateTool.isDoNotDisturb(userId)) {
            return 0;
        }

        if (!peekStateTool.isCooldownPassed(userId)) {
            return 0;
        }

        if (peekStateTool.isWakeupMutex(userId)) {
            return 0;
        }

        log.debug("peek 硬过滤通过：userId={}", userId);

        double probability = peekStateTool.calculatePeekProbability(userId, timeContext);
        if (ThreadLocalRandom.current().nextDouble() >= probability) {
            log.debug("peek 概率未通过：userId={}, probability={}", userId,
                    String.format("%.3f", probability));
            return 1;
        }

        log.info("peek 概率通过：userId={}, probability={}, activeMinutes={}",
                userId, String.format("%.3f", probability),
                peekStateTool.getContinuousActiveMinutes(userId));

        // 全局速率限制
        Long current = redisTemplate.opsForValue().increment(PEEK_RATE_LIMIT_KEY);
        if (current == 1) {
            redisTemplate.expire(PEEK_RATE_LIMIT_KEY, RATE_LIMIT_WINDOW);
        }
        if (current > maxConcurrentRequests) {
            log.warn("peek 全局速率限制，跳过：userId={}", userId);
            return 1;
        }

        String peekId = UUID.randomUUID().toString();

        String redisKey = PEEK_PENDING_KEY_PREFIX + peekId;
        redisTemplate.opsForValue().set(redisKey, userId, Duration.ofSeconds(peekRequestTtlSeconds));
        chatPushService.pushPeekRequest(userId, peekId);

        log.info("peek 请求已发送：userId={}, peekId={}", userId, peekId);
        return 2;
    }
}
