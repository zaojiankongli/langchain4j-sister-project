package com.zjkl.user.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.user.service.InterestTagGenerateService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestTagGenerateSchedulerTest {

    @Mock
    private InterestTagGenerateService interestTagGenerateService;
    @Mock
    private UserActivityTracker userActivityTracker;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private InterestTagGenerateScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InterestTagGenerateScheduler(interestTagGenerateService, userActivityTracker, redisTemplate);
    }

    @Test
    void generateDayInterestTagsAtDate_shouldSkipWhenDistributedDedupAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("interest-tag:scheduler:" + LocalDate.now() + ":u1"),
                eq("1"),
                eq(Duration.ofDays(1))
        )).thenReturn(false);

        scheduler.generateDayInterestTagsAtDate(LocalDate.now());

        verify(interestTagGenerateService, never()).generateTags("u1");
    }

    @Test
    void generateDayInterestTagsAtDate_shouldReleaseDedupWhenGenerationFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("interest-tag:scheduler:" + LocalDate.now() + ":u1"),
                eq("1"),
                eq(Duration.ofDays(1))
        )).thenReturn(true);
        doThrow(new RuntimeException("generate failed")).when(interestTagGenerateService).generateTags("u1");

        scheduler.generateDayInterestTagsAtDate(LocalDate.now());

        verify(redisTemplate).delete("interest-tag:scheduler:" + LocalDate.now() + ":u1");
    }

    @Test
    void generateDayInterestTagsAtDate_shouldKeepDedupWhenGenerationReturnsEmptyTags() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userActivityTracker.getActiveMemoryIdsInLastDays(1, 200)).thenReturn(Set.of("u1"));
        when(valueOperations.setIfAbsent(
                eq("interest-tag:scheduler:" + LocalDate.now() + ":u1"),
                eq("1"),
                eq(Duration.ofDays(1))
        )).thenReturn(true);
        when(interestTagGenerateService.generateTags("u1")).thenReturn(List.of());

        scheduler.generateDayInterestTagsAtDate(LocalDate.now());

        verify(redisTemplate, never()).delete("interest-tag:scheduler:" + LocalDate.now() + ":u1");
    }
}
