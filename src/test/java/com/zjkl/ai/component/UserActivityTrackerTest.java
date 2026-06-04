package com.zjkl.ai.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityTrackerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private UserActivityTracker userActivityTracker;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        userActivityTracker = new UserActivityTracker(redisTemplate);
    }

    @Test
    void recordActivity_shouldRefreshTrackedUsersTtlForExistingMember() {
        when(zSetOperations.add(eq("user:activity:tracked"), eq("u1"), anyDouble())).thenReturn(false);

        userActivityTracker.recordActivity("u1");

        verify(redisTemplate).expire("user:activity:tracked", Duration.ofDays(7));
    }

    @Test
    void recordActivity_shouldKeepSessionStartWhenActivityGapWithinFiveMinutes() {
        long now = System.currentTimeMillis();
        when(valueOperations.get("user:activity:last_active:u1")).thenReturn(String.valueOf(now - Duration.ofMinutes(4).toMillis()));

        userActivityTracker.recordActivity("u1");

        verify(valueOperations).get("user:activity:last_active:u1");
        verify(valueOperations, org.mockito.Mockito.never()).set(eq("user:activity:session_start:u1"), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void recordActivity_shouldResetSessionStartWhenActivityGapExceedsFiveMinutes() {
        long now = System.currentTimeMillis();
        when(valueOperations.get("user:activity:last_active:u1")).thenReturn(String.valueOf(now - Duration.ofMinutes(6).toMillis()));

        userActivityTracker.recordActivity("u1");

        verify(valueOperations).set(eq("user:activity:session_start:u1"), anyString(), eq(Duration.ofDays(7)));
    }
}
