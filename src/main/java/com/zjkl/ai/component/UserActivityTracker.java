package com.zjkl.ai.component;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@Component
public class UserActivityTracker {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LAST_ACTIVE_PREFIX = "user:activity:last_active:";
    private static final String TRACKED_USERS_ZSET = "user:activity:tracked";
    private static final Duration ZSET_TTL = Duration.ofDays(7);

    public UserActivityTracker(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordActivity(String memoryId) {
        long now = System.currentTimeMillis();

        redisTemplate.opsForValue().set(
                LAST_ACTIVE_PREFIX + memoryId,
                String.valueOf(now),
                ZSET_TTL
        );

        boolean isNewMember = redisTemplate.opsForZSet().add(TRACKED_USERS_ZSET, memoryId, (double) now);
        if (isNewMember) {
            redisTemplate.expire(TRACKED_USERS_ZSET, ZSET_TTL);
        }
    }

    /** 获取活跃用户 */
    public Set<String> getActiveMemoryIdsInLastDays(int days) {
        long minScore = System.currentTimeMillis() - (long) days * 24 * 3600 * 1000;
        Set<String> ids = redisTemplate.opsForZSet()
                .rangeByScore(TRACKED_USERS_ZSET, minScore, Double.MAX_VALUE);
        return ids != null ? ids : Collections.emptySet();
    }

    /** 获取最后活跃时间 */
    public Long getLastActiveTime(String memoryId) {
        String value = redisTemplate.opsForValue().get(LAST_ACTIVE_PREFIX + memoryId);
        return value != null ? Long.parseLong(value) : null;
    }
}
