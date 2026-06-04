package com.zjkl.common.context;

import com.zjkl.common.config.properties.AuthProperties;
import org.springframework.stereotype.Component;

/**
 * 用户上下文 - 基于 ThreadLocal
 * <p>
 * 数据流：ThreadLocal（最快，无竞争，主路径）
 */
@Component
public class UserContext {

    private static final ThreadLocal<String> userIdHolder = ThreadLocal.withInitial(() -> null);

    public void setUserId(String userId) {
        userIdHolder.set(userId);
    }

    public String getUserId() {
        return userIdHolder.get();
    }

    public void clear() {
        userIdHolder.remove();
    }

    /**
     * 校验当前用户是否有管理员权限。
     *
     * @param authProperties 认证配置，用于判断管理员身份
     * @return null 表示有权限，否则返回错误消息字符串
     * @deprecated 耦合了 AuthProperties 到上下文类。建议使用 {@code checkSelfAccessCode} 的模式，
     *             将管理员校验迁移到独立的 AdminAccessChecker 或返回状态码而非字符串。
     */
    @Deprecated
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
}
