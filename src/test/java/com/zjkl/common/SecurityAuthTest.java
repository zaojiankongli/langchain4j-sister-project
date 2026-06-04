package com.zjkl.common;

import com.zjkl.common.context.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全测试 - 验证用户上下文隔离和权限检查机制
 * <p>
 * 测试覆盖：
 * 1. UserContext 线程隔离 - 不同线程的用户ID互不干扰
 * 2. UserContext 清空 - clear() 后 getUserId() 返回 null
 * 3. UserContext 虚拟线程兼容 - 重复设置不抛异常
 */
class SecurityAuthTest {

    private UserContext userContext;

    @BeforeEach
    void setUp() {
        userContext = new UserContext();
    }

    @Test
    void userContext_shouldIsolateBetweenThreads() throws Exception {
        // 主线程设置用户A
        userContext.setUserId("user-A");
        assertEquals("user-A", userContext.getUserId());

        // 子线程应该是独立的（初始为 null）
        String[] childUserId = {null};
        Thread child = new Thread(() -> {
            childUserId[0] = userContext.getUserId();
        });
        child.start();
        child.join();

        assertNull(childUserId[0], "子线程不应继承主线程的 UserContext");

        // 主线程仍然是 user-A
        assertEquals("user-A", userContext.getUserId());
    }

    @Test
    void userContext_clear_shouldResetUserId() {
        userContext.setUserId("user-B");
        assertEquals("user-B", userContext.getUserId());

        userContext.clear();
        assertNull(userContext.getUserId(), "clear() 后 userId 应为 null");
    }

    @Test
    void userContext_shouldAllowMultipleSets() {
        userContext.setUserId("user-C");
        assertEquals("user-C", userContext.getUserId());

        userContext.setUserId("user-D");
        assertEquals("user-D", userContext.getUserId(), "重复设置应更新 userId");
    }

    @Test
    void userContext_nullUserId_shouldBeHandled() {
        // 未设置时返回 null（不抛异常）
        assertNull(userContext.getUserId());

        // 设置 null 值
        userContext.setUserId(null);
        // ThreadLocal.withInitial 的初始值为 null，所以 set(null) 后 get() 返回 null
        assertNull(userContext.getUserId());
    }

    @Test
    void userContext_clearWithoutSet_shouldNotThrow() {
        // 从未 set 直接 clear 不抛异常
        assertDoesNotThrow(() -> userContext.clear());
        // clear 后再 clear 也不抛异常
        assertDoesNotThrow(() -> userContext.clear());
    }
}
