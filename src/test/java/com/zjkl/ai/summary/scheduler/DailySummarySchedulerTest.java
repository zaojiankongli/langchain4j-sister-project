package com.zjkl.ai.summary.scheduler;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.memory.store.RedisChatMemoryStore;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.zjkl.ai.summary.config.RedisStreamConfig.SUMMARY_STREAM;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySummarySchedulerTest {

    @Mock
    private RedisChatMemoryStore redisChatMemoryStore;

    @Mock
    private UserActivityTracker userActivityTracker;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private DailySummaryScheduler dailySummaryScheduler;

    @BeforeEach
    void setUp() {
        dailySummaryScheduler = new DailySummaryScheduler(redisChatMemoryStore, userActivityTracker, redisTemplate);
    }

    @Test
    void processUserMemory_shouldSkipWhenDailyTaskCooldownAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("daily-summary:scheduler:" + LocalDate.now() + ":u1"),
                eq("1"),
                eq(Duration.ofDays(1))
        )).thenReturn(false);

        dailySummaryScheduler.processUserMemory("u1");

        verify(redisTemplate, never()).opsForStream();
        verify(redisTemplate, never()).delete("daily-summary:scheduler:" + LocalDate.now() + ":u1");
    }

    @Test
    void processUserMemory_shouldReleaseDedupKeyWhenStreamEnqueueFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(redisChatMemoryStore.getMessages("u1")).thenReturn(List.of(UserMessage.from("你好")));
        when(valueOperations.get("chat:summary:u1")).thenReturn("");
        when(valueOperations.setIfAbsent(
                eq("daily-summary:scheduler:" + LocalDate.now() + ":u1"),
                eq("1"),
                eq(Duration.ofDays(1))
        )).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("stream failed"))
                .when(streamOperations)
                .add(eq(SUMMARY_STREAM), anyMap());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> dailySummaryScheduler.processUserMemory("u1"));

        verify(redisTemplate).delete("daily-summary:scheduler:" + LocalDate.now() + ":u1");
    }
}
