package com.zjkl.ai.summary.consumer;

import com.zjkl.ai.summary.service.DailySummaryProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryGenerationConsumerDeadLetterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private DailySummaryProcessor dailySummaryProcessor;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MapRecord<String, Object, Object> record;

    private SummaryGenerationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SummaryGenerationConsumer(redisTemplate, dailySummaryProcessor, redissonClient);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void processMessage_shouldIncludeStackTraceWhenMovedToDeadLetterQueue() throws Exception {
        Map<Object, Object> value = new HashMap<>();
        value.put("taskId", "task-1");
        value.put("userId", "user-1");
        value.put("conversationText", "conversation");
        value.put("previousSummary", "summary");
        value.put("createdAt", "2026-06-04T00:00:00");

        RuntimeException failure = new RuntimeException("boom");

        when(record.getValue()).thenReturn(value);
        when(setOperations.isMember(anyString(), anyString())).thenReturn(false);
        when(redissonClient.getLock("daily-summary-lock:task-1")).thenReturn(lock);
        when(lock.tryLock(1, 30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(valueOperations.increment("daily-summary:retry:task-1")).thenReturn(3L);

        org.mockito.Mockito.doThrow(failure)
                .when(dailySummaryProcessor)
                .processTask("task-1", "user-1", "conversation", "summary", "2026-06-04T00:00:00");

        consumer.processMessage(record);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> deadLetterCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(streamOperations).add(eq("daily-summary:dead-letter"), deadLetterCaptor.capture());

        Map<String, Object> deadLetter = deadLetterCaptor.getValue();
        assertEquals("boom", deadLetter.get("error"));
        assertTrue(String.valueOf(deadLetter.get("stackTrace")).contains("RuntimeException"), "死信消息应保留堆栈信息");
        verify(streamOperations).acknowledge("summary-consumer-group", record);
        verify(lock).unlock();
    }
}
