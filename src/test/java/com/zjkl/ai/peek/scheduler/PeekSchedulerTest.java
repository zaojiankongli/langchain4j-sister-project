package com.zjkl.ai.peek.scheduler;

import com.zjkl.ai.chat.stomp.ChatPushService;
import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.ai.peek.tool.PeekStateTool;
import com.zjkl.common.config.properties.PeekProperties;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeekSchedulerTest {

    @Mock
    private UserActivityTracker userActivityTracker;
    @Mock
    private UserStateTool userStateTool;
    @Mock
    private TimeContextTool timeContextTool;
    @Mock
    private PeekStateTool peekStateTool;
    @Mock
    private ChatPushService chatPushService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PeekProperties peekProperties;
    private PeekScheduler scheduler;

    @BeforeEach
    void setUp() {
        peekProperties = new PeekProperties();
        peekProperties.setEnabled(true);
        peekProperties.setPeekRequestTtlSeconds(120);
        peekProperties.setMaxConcurrentRequests(5);

        scheduler = new PeekScheduler(
                userActivityTracker,
                userStateTool,
                timeContextTool,
                peekStateTool,
                chatPushService,
                redisTemplate,
                peekProperties
        );

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(peekStateTool.isPeekEnabled("u1")).thenReturn(true);
        lenient().when(chatPushService.isUserConnected("u1")).thenReturn(true);
        lenient().when(peekStateTool.isUserActive("u1")).thenReturn(true);
        lenient().when(userStateTool.isDoNotDisturb("u1")).thenReturn(false);
        lenient().when(peekStateTool.isCooldownPassed("u1")).thenReturn(true);
        lenient().when(peekStateTool.isWakeupMutex("u1")).thenReturn(false);
        lenient().when(peekStateTool.calculatePeekProbability(eq("u1"), org.mockito.ArgumentMatchers.any())).thenReturn(1.0);
        lenient().when(peekStateTool.getContinuousActiveMinutes("u1")).thenReturn(60);
        // Default: Lua 脚本速率限制返回 1（不超限）
        lenient().when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString())).thenReturn(1L);
    }

    @Test
    void processUserPeek_shouldSkipWhenPerUserDistributedMutexAlreadyExists() {
        var timeContext = new TimeContextTool.TimeContext("12:00", "中午", "午餐时间", "3", false, "中午好");
        when(valueOperations.setIfAbsent("peek:request-lock:u1", "1", Duration.ofSeconds(120))).thenReturn(false);

        int result = (int) ReflectionTestUtils.invokeMethod(scheduler, "processUserPeek", "u1", timeContext);

        assertEquals(1, result);
        verify(chatPushService, never()).pushPeekRequest(eq("u1"), anyString());
    }

    @Test
    void processUserPeek_shouldReleasePerUserMutexWhenPushFails() {
        var timeContext = new TimeContextTool.TimeContext("12:00", "中午", "午餐时间", "3", false, "中午好");
        when(valueOperations.setIfAbsent("peek:request-lock:u1", "1", Duration.ofSeconds(120))).thenReturn(true);
        doThrow(new RuntimeException("push failed")).when(chatPushService).pushPeekRequest(eq("u1"), anyString());

        assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(scheduler, "processUserPeek", "u1", timeContext));

        verify(redisTemplate).delete("peek:request-lock:u1");
    }

    @Test
    void processUserPeek_shouldReleasePerUserMutexWhenGlobalRateLimitRejects() {
        var timeContext = new TimeContextTool.TimeContext("12:00", "中午", "午餐时间", "3", false, "中午好");
        when(valueOperations.setIfAbsent("peek:request-lock:u1", "1", Duration.ofSeconds(120))).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString())).thenReturn(6L);

        int result = (int) ReflectionTestUtils.invokeMethod(scheduler, "processUserPeek", "u1", timeContext);

        assertEquals(0, result);
        verify(redisTemplate).delete("peek:request-lock:u1");
        verify(chatPushService, never()).pushPeekRequest(eq("u1"), anyString());
    }

    @Test
    void checkUsersForPeek_shouldProcessUsersInBatches() {
        var timeContext = new TimeContextTool.TimeContext("12:00", "中午", "午餐时间", "3", false, "中午好");
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        peekProperties.setMaxConcurrentRequests(3);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8"));
        when(timeContextTool.getCurrentContext()).thenReturn(timeContext);
        when(peekStateTool.isPeekEnabled(anyString())).thenAnswer(invocation -> {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(120);
                return false;
            } finally {
                inFlight.decrementAndGet();
            }
        });

        scheduler.checkUsersForPeek();

        assertEquals(8, maxInFlight.get() <= 3 ? 8 : -1);
        verify(peekStateTool, times(8)).isPeekEnabled(anyString());
    }
}
