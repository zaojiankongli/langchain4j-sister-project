package com.zjkl.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RateLimiter {

    private static final String LUA_SCRIPT =
        "local key = KEYS[1] " +
        "local now = tonumber(ARGV[1]) " +
        "local window = tonumber(ARGV[2]) " +
        "local max = tonumber(ARGV[3]) " +
        "redis.call('zremrangebyscore', key, 0, now - window) " +
        "local count = redis.call('zcard', key) " +
        "if count < max then " +
        "    redis.call('zadd', key, now, now .. '-' .. math.random(1000000)) " +
        "    redis.call('pexpire', key, window) " +
        "    return 1 " +
        "end " +
        "return 0";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    /**
     * @param key        限流 key
     * @param maxRequests 窗口内最大请求数
     * @param windowMs    窗口大小（毫秒）
     * @return true 如果允许通过，false 如果被限流
     */
    public boolean tryAcquire(String key, int maxRequests, long windowMs) {
        Long result = redisTemplate.execute(script, List.of(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowMs),
                String.valueOf(maxRequests));
        return result != null && result == 1;
    }
}
