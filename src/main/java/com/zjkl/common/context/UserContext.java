package com.zjkl.common.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zjkl.common.config.properties.AuthProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 用户上下文 - 适配虚拟线程（Java 21+）
 * <p>
 * ThreadLocal 在虚拟线程中不可靠（虚拟线程复用载体线程时残留数据）。
 * 使用 Caffeine 缓存代替 ConcurrentHashMap 兜底，自动过期防止内存泄漏。
 * <p>
 * 数据流：
 * 1. ThreadLocal（最快，无竞争，主路径）
 * 2. Caffeine 缓存（兜底，keyed by 线程 ID，10 分钟自动过期）
 */
@Component
public class UserContext {

    private static final ThreadLocal<String> userIdHolder = ThreadLocal.withInitial(() -> null);

    /**
     * 虚拟线程兜底缓存 - 自动过期，防止 ThreadLocal 失效时数据丢失
     * 缓存大小为 10000，10 分钟无访问自动过期
     */
    private final Cache<Long, String> fallbackCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public void setUserId(String userId) {
        try {
            userIdHolder.set(userId);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // 虚拟线程可能不支持 ThreadLocal，fallback 到 Caffeine 缓存
            fallbackCache.put(Thread.currentThread().threadId(), userId);
        }
    }

    public String getUserId() {
        try {
            String uid = userIdHolder.get();
            if (uid != null) return uid;
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            // fall through
        }
        return fallbackCache.getIfPresent(Thread.currentThread().threadId());
    }

    public void clear() {
        try {
            userIdHolder.remove();
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            // fall through
        }
        fallbackCache.invalidate(Thread.currentThread().threadId());
    }

    /**
     * 校验当前用户是否有权访问目标用户的资源（仅限本人访问）。
     *
     * @param targetUserId 路径参数中的目标用户 ID
     * @return null 表示校验通过；否则返回错误信息字符串
     */
    public String checkSelfAccess(String targetUserId) {
        String current = getUserId();
        if (current == null) {
            return "请先登录";
        }
        if (!current.equals(targetUserId)) {
            return "无权访问其他用户的数据";
        }
        return null;
    }

    /**
     * 校验当前用户是否有管理员权限。
     *
     * @param authProperties 认证配置，用于判断管理员身份
     * @return null 表示有权限，否则返回错误消息字符串
     */
    public String checkAdminAccess(AuthProperties authProperties) {
        String userId = getUserId();
        if (userId == null) {
            return "请先登录";
        }
        if (!authProperties.isAdmin(userId)) {
            return "需要管理员权限";
        }
        return null;
    }

    /**
     * 校验当前用户是否有权访问目标用户的资源。
     * @return 0=通过, 401=未登录, 403=无权访问
     */
    public int checkSelfAccessCode(String targetUserId) {
        String current = getUserId();
        if (current == null) return 401;
        if (!current.equals(targetUserId)) return 403;
        return 0;
    }

    @PreDestroy
    public void cleanup() {
        fallbackCache.cleanUp();
    }
}
