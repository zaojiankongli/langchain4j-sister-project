package com.zjkl.ai.summary.controller;

import com.zjkl.ai.summary.scheduler.DailySummaryScheduler;
import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueMonitorControllerTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private DailySummaryScheduler dailySummaryScheduler;
    @Mock
    private UserContext userContext;
    @Mock
    private AuthProperties authProperties;

    private QueueMonitorController controller;

    @BeforeEach
    void setUp() {
        controller = new QueueMonitorController(redisTemplate, dailySummaryScheduler, userContext, authProperties);
    }

    @Test
    void healthCheck_shouldReturnUnauthorizedWhenUserContextMissing() {
        when(userContext.checkAdminAccess(authProperties)).thenReturn("请先登录");

        Result<?> result = controller.healthCheck();

        assertEquals(401, result.getCode());
        assertEquals("请先登录", result.getMessage());
    }

    @Test
    void healthCheck_shouldReturnForbiddenWhenNotAdmin() {
        when(userContext.checkAdminAccess(authProperties)).thenReturn("需要管理员权限");

        Result<?> result = controller.healthCheck();

        assertEquals(401, result.getCode());
        assertEquals("需要管理员权限", result.getMessage());
    }
}
