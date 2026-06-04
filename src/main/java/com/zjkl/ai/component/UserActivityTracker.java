package com.zjkl.ai.component;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@Component
public class UserActivityTracker {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LAST_ACTIVE_PREFIX = "user:activity:last_active:";
    private static final String SESSION_START_PREFIX = "user:activity:session_start:";
    private static final String TRACKED_USERS_ZSET = "user:activity:tracked";
    private static final Duration ZSET_TTL = Duration.ofDays(7);
    private static final Duration ACTIVE_SESSION_GAP = Duration.ofMinutes(5);

    public UserActivityTracker(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordActivity(String memoryId) {
        long now = System.currentTimeMillis();

        String lastActiveKey = LAST_ACTIVE_PREFIX + memoryId;
        String sessionStartKey = SESSION_START_PREFIX + memoryId;
        String previousLastActive = redisTemplate.opsForValue().get(lastActiveKey);

        if (previousLastActive == null || isSessionExpired(previousLastActive, now)) {
            redisTemplate.opsForValue().set(sessionStartKey, String.valueOf(now), ZSET_TTL);
        }

        redisTemplate.opsForValue().set(
                lastActiveKey,
                String.valueOf(now),
                ZSET_TTL
        );

        redisTemplate.opsForZSet().add(TRACKED_USERS_ZSET, memoryId, (double) now);
        redisTemplate.opsForZSet().removeRangeByScore(TRACKED_USERS_ZSET, 0, now - ZSET_TTL.toMillis());
        redisTemplate.expire(TRACKED_USERS_ZSET, ZSET_TTL);
    }

    /** 获取活跃用户 */
    public Set<String> getActiveMemoryIdsInLastDays(int days) {
        return getActiveMemoryIdsInLastDays(days, 0);
    }

    /** 获取活跃用户（可限制返回数量，按最近活跃优先） */
    public Set<String> getActiveMemoryIdsInLastDays(int days, int maxUsers) {
        long minScore = System.currentTimeMillis() - (long) days * 24 * 3600 * 1000;
        try {
            ZSetOperations<String, String> zSetOperations = redisTemplate.opsForZSet();
            if (zSetOperations == null) {
                return Collections.emptySet();
            }
            Set<String> ids = maxUsers > 0
                    ? zSetOperations.reverseRangeByScore(TRACKED_USERS_ZSET, minScore, Double.MAX_VALUE, 0, maxUsers)
                    : zSetOperations.rangeByScore(TRACKED_USERS_ZSET, minScore, Double.MAX_VALUE);
            return ids != null ? ids : Collections.emptySet();
        } catch (RuntimeException e) {
            return Collections.emptySet();
        }
    }

    /** 获取最后活跃时间 */
    public Long getLastActiveTime(String memoryId) {
        String value = redisTemplate.opsForValue().get(LAST_ACTIVE_PREFIX + memoryId);
        return value != null ? Long.parseLong(value) : null;
    }

    public Long getSessionStartTime(String memoryId) {
        String value = redisTemplate.opsForValue().get(SESSION_START_PREFIX + memoryId);
        return value != null ? Long.parseLong(value) : null;
    }

    private boolean isSessionExpired(String previousLastActive, long now) {
        try {
            long lastActive = Long.parseLong(previousLastActive);
            return now - lastActive > ACTIVE_SESSION_GAP.toMillis();
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
