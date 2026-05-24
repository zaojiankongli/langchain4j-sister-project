package com.zjkl.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RateLimiter {

    private static final String LUA_SCRIPT =
        "local count = redis.call('incr', KEYS[1]) " +
        "if count == 1 then " +
        "    redis.call('pexpire', KEYS[1], ARGV[1]) " +
        "end " +
        "return count";

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
        Long count = redisTemplate.execute(script, List.of(key), String.valueOf(windowMs));
        return count != null && count <= maxRequests;
    }
}
