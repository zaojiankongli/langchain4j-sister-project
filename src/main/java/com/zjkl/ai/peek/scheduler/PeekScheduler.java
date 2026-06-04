package com.zjkl.ai.peek.scheduler;

import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.ai.peek.tool.PeekStateTool;
import com.zjkl.common.config.properties.PeekProperties;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Peek 定时调度 — 在线+活跃用户定期截图请求
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeekScheduler {

    private static final String PEEK_REQUEST_LOCK_KEY_PREFIX = "peek:request-lock:";
    private static final int MAX_ACTIVE_USERS_TO_SCAN = 200;
    private final UserActivityTracker userActivityTracker;
    private final UserStateTool userStateTool;
    private final TimeContextTool timeContextTool;
    private final PeekStateTool peekStateTool;
    private final ChatPushService chatPushService;
    private final StringRedisTemplate redisTemplate;
    private final PeekProperties peekProperties;

    private static final String PEEK_PENDING_KEY_PREFIX = "peek:pending:";
    private static final String PEEK_RATE_LIMIT_KEY = "peek:rate_limit:current";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(30);

    /**
     * Lua 脚本：原子递增 + 设置过期时间，防止 INCR 与 EXPIRE 之间的进程崩溃
     * 导致速率限制键永不过期。
     * KEYS[1] = rate limit key, ARGV[1] = TTL seconds
     */
    private static final String RATE_LIMIT_LUA =
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n" +
            "end\n" +
            "return current";
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);
    private static final Executor PEEK_EXECUTOR = Thread::startVirtualThread;

    @Scheduled(cron = "0 0/20 8-22 * * ?")
    public void checkUsersForPeek() {
        if (!peekProperties.isEnabled()) {
            log.debug("peek 功能已禁用");
            return;
        }

        Set<String> activeUsers = userActivityTracker.getActiveMemoryIdsInLastDays(1, MAX_ACTIVE_USERS_TO_SCAN);
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

        List<String> users = new ArrayList<>(activeUsers);
        int batchSize = Math.max(1, peekProperties.getMaxConcurrentRequests());
        for (int i = 0; i < users.size(); i += batchSize) {
            List<String> batch = users.subList(i, Math.min(i + batchSize, users.size()));
            CompletableFuture<?>[] futures = batch.stream()
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
        }

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

        String requestLockKey = PEEK_REQUEST_LOCK_KEY_PREFIX + userId;
        Boolean requestLockAcquired = redisTemplate.opsForValue().setIfAbsent(
                requestLockKey,
                "1",
                Duration.ofSeconds(peekProperties.getPeekRequestTtlSeconds())
        );
        if (!Boolean.TRUE.equals(requestLockAcquired)) {
            log.debug("peek 用户请求互斥命中，跳过：userId={}", userId);
            return 1;
        }

        // 全局速率限制（Lua 脚本原子递增 + 过期，防止 INCR/EXPIRE 间崩溃导致键永不过期）
        long current = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(PEEK_RATE_LIMIT_KEY),
                String.valueOf(RATE_LIMIT_WINDOW.getSeconds()));
        if (current > peekProperties.getMaxConcurrentRequests()) {
            redisTemplate.delete(requestLockKey);
            log.warn("peek 全局速率限制，跳过：userId={}", userId);
            return 1;
        }

        String redisKey = null;
        try {
            String peekId = UUID.randomUUID().toString();

            redisKey = PEEK_PENDING_KEY_PREFIX + peekId;
            redisTemplate.opsForValue().set(redisKey, userId, Duration.ofSeconds(peekProperties.getPeekRequestTtlSeconds()));
            chatPushService.pushPeekRequest(userId, peekId);

            log.info("peek 请求已发送：userId={}, peekId={}", userId, peekId);
            return 2;
        } catch (RuntimeException e) {
            if (redisKey != null) {
                redisTemplate.delete(redisKey);
            }
            redisTemplate.delete(requestLockKey);
            throw e;
        }
    }
}
