package com.zjkl.ai.component;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class UserActivityTracker {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LAST_ACTIVE_PREFIX = "user:activity:last_active:";
    private static final String SESSION_START_PREFIX = "user:activity:session_start:";
    private static final String TRACKED_USERS_ZSET = "user:activity:tracked";
    private static final Duration ZSET_TTL = Duration.ofDays(7);
    private static final Duration ACTIVE_SESSION_GAP = Duration.ofMinutes(5);

    /**
     * Lua 脚本：将 5 次 Redis 调用合并为 1 次原子操作
     * KEYS[1] = last_active_key, KEYS[2] = session_start_key, KEYS[3] = zset_key
     * ARGV[1] = now, ARGV[2] = gap_millis, ARGV[3] = ttl_seconds, ARGV[4] = min_score (for cleanup)
     */
    private static final String RECORD_ACTIVITY_LUA = """
            local lastActive = redis.call('GET', KEYS[1])
            local now = tonumber(ARGV[1])
            local gap = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            local minScore = tonumber(ARGV[4])
            
            -- 如果上次活跃时间为空或已过期，重置会话起点
            if not lastActive or (now - tonumber(lastActive)) > gap then
                redis.call('SET', KEYS[2], ARGV[1], 'EX', ttl)
            end
            
            -- 更新最后活跃时间
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ttl)
            
            -- 添加到有序集合
            redis.call('ZADD', KEYS[3], now, ARGV[5])
            
            -- 清理过期条目
            redis.call('ZREMRANGEBYSCORE', KEYS[3], 0, minScore)
            
            -- 刷新 TTL
            redis.call('EXPIRE', KEYS[3], ttl)
            
            return 1
            """;

    private final DefaultRedisScript<Long> recordActivityScript;

    public UserActivityTracker(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.recordActivityScript = new DefaultRedisScript<>(RECORD_ACTIVITY_LUA, Long.class);
    }

    public void recordActivity(String memoryId) {
        long now = System.currentTimeMillis();

        String lastActiveKey = LAST_ACTIVE_PREFIX + memoryId;
        String sessionStartKey = SESSION_START_PREFIX + memoryId;
        long ttlSeconds = ZSET_TTL.toMillis() / 1000;
        long minScore = now - ZSET_TTL.toMillis();

        try {
            redisTemplate.execute(
                    recordActivityScript,
                    List.of(lastActiveKey, sessionStartKey, TRACKED_USERS_ZSET),
                    String.valueOf(now),
                    String.valueOf(ACTIVE_SESSION_GAP.toMillis()),
                    String.valueOf(ttlSeconds),
                    String.valueOf(minScore),
                    memoryId
            );
        } catch (RuntimeException e) {
            // Lua 脚本执行失败时回退到非原子操作，保证功能可用
            fallbackRecordActivity(memoryId, now);
        }
    }

    /**
     * 回退方案：当 Lua 脚本执行失败时使用（如 Redis 版本不支持）
     * 
     * 已知限制：此方法包含 5 次独立 Redis 调用，非原子操作。在极端并发场景下
     * （如同一 memoryId 在同一毫秒内被多个线程回退处理），可能出现中间状态不一致。
     * 由于这是 Lua 脚本的降级路径（极少触发），此风险可接受。
     */
    private void fallbackRecordActivity(String memoryId, long now) {
        String lastActiveKey = LAST_ACTIVE_PREFIX + memoryId;
        String sessionStartKey = SESSION_START_PREFIX + memoryId;
        String previousLastActive = redisTemplate.opsForValue().get(lastActiveKey);

        if (previousLastActive == null || isSessionExpired(previousLastActive, now)) {
            redisTemplate.opsForValue().set(sessionStartKey, String.valueOf(now), ZSET_TTL);
        }

        redisTemplate.opsForValue().set(lastActiveKey, String.valueOf(now), ZSET_TTL);
        var zSetOps = redisTemplate.opsForZSet();
        if (zSetOps != null) {
            zSetOps.add(TRACKED_USERS_ZSET, memoryId, (double) now);
            zSetOps.removeRangeByScore(TRACKED_USERS_ZSET, 0, now - ZSET_TTL.toMillis());
        }
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
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long getSessionStartTime(String memoryId) {
        String value = redisTemplate.opsForValue().get(SESSION_START_PREFIX + memoryId);
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
