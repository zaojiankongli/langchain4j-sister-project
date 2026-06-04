package com.zjkl.emotion.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.emotion.service.EmotionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmotionRecordSchedulerTest {

    @Mock
    private EmotionRecordService emotionRecordService;
    @Mock
    private UserActivityTracker userActivityTracker;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmotionRecordScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EmotionRecordScheduler(emotionRecordService, userActivityTracker, redisTemplate);
    }

    @Test
    void recordUserEmotionsAtHour_shouldSkipWhenDistributedSlotDedupAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("emotion-record:" + LocalDate.now() + ":12:u1"),
                eq("1"),
                eq(Duration.ofHours(4))
        )).thenReturn(false);

        scheduler.recordUserEmotionsAtHour(12);

        verify(emotionRecordService, never()).recordEmotionAsync("u1");
    }

    @Test
    void recordUserEmotionsAtHour_shouldReleaseSlotDedupWhenSubmissionFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("emotion-record:" + LocalDate.now() + ":12:u1"),
                eq("1"),
                eq(Duration.ofHours(4))
        )).thenReturn(true);
        doThrow(new RuntimeException("submit failed")).when(emotionRecordService).recordEmotionAsync("u1");

        scheduler.recordUserEmotionsAtHour(12);

        verify(redisTemplate).delete("emotion-record:" + LocalDate.now() + ":12:u1");
    }
}
