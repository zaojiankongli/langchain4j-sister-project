package com.zjkl.recommendation.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationSchedulerTest {

    @Mock
    private RecommendationService recommendationService;
    @Mock
    private UserActivityTracker userActivityTracker;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecommendationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RecommendationScheduler(recommendationService, userActivityTracker, redisTemplate);
    }

    @Test
    void generateDailyRecommendations_shouldSkipWhenDistributedDedupAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("recommendation:scheduler:" + LocalDate.now() + ":u1"),
                eq("1"),
                eq(Duration.ofDays(1))
        )).thenReturn(false);

        scheduler.generateDailyRecommendations();

        verify(recommendationService, never()).generateRecommendations("u1");
    }

    @Test
    void generateDailyRecommendations_shouldReleaseDedupWhenGenerationFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("recommendation:scheduler:" + LocalDate.now() + ":u1"),
                eq("1"),
                eq(Duration.ofDays(1))
        )).thenReturn(true);
        doThrow(new RuntimeException("generate failed")).when(recommendationService).generateRecommendations("u1");

        scheduler.generateDailyRecommendations();

        verify(redisTemplate).delete("recommendation:scheduler:" + LocalDate.now() + ":u1");
    }

    @Test
    void generateDailyRecommendations_shouldProcessUsersBeyondFirstBatch() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1", "u2", "u3", "u4", "u5"));
        when(valueOperations.setIfAbsent(any(), eq("1"), eq(Duration.ofDays(1)))).thenReturn(true);

        AtomicInteger callCount = new AtomicInteger();
        doAnswer(invocation -> {
            callCount.incrementAndGet();
            Thread.sleep(20);
            return List.of();
        }).when(recommendationService).generateRecommendations(any());

        scheduler.generateDailyRecommendations();

        assertEquals(5, callCount.get());
    }
}
