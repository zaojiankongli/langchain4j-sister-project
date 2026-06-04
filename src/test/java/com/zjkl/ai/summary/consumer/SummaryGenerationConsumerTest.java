package com.zjkl.ai.summary.consumer;

import com.zjkl.ai.summary.service.DailySummaryProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zjkl.ai.summary.config.RedisStreamConfig.SUMMARY_STREAM;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryGenerationConsumerTest {

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
    private MapRecord<String, Object, Object> record;

    private SummaryGenerationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SummaryGenerationConsumer(redisTemplate, dailySummaryProcessor, redissonClient);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void processMessage_shouldAcceptNonStringStreamValues() throws Exception {
        Map<Object, Object> value = new HashMap<>();
        value.put("taskId", 12345L);
        value.put("userId", 67890L);
        value.put("conversationText", 24680L);
        value.put("previousSummary", 13579L);
        value.put("createdAt", "2026-06-04T00:00:00");

        when(record.getValue()).thenReturn(value);
        when(setOperations.isMember(anyString(), anyString())).thenReturn(false);
        when(redissonClient.getLock("daily-summary-lock:12345")).thenReturn(lock);
        when(lock.tryLock(1, 120, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        consumer.processMessage(record);

        verify(dailySummaryProcessor).processTask(
                "12345",
                "67890",
                "24680",
                "13579",
                "2026-06-04T00:00:00"
        );
        verify(redisTemplate).expire(anyString(), eq(24L), eq(TimeUnit.HOURS));
        verify(streamOperations).acknowledge("summary-consumer-group", record);
        verify(lock).unlock();
    }

    @Test
    void processMessage_shouldNotAckWhenLockNotAcquired() throws Exception {
        Map<Object, Object> value = new HashMap<>();
        value.put("taskId", 12345L);
        value.put("userId", 67890L);

        when(record.getValue()).thenReturn(value);
        when(setOperations.isMember(anyString(), anyString())).thenReturn(false);
        when(redissonClient.getLock("daily-summary-lock:12345")).thenReturn(lock);
        when(lock.tryLock(1, 120, TimeUnit.SECONDS)).thenReturn(false);

        consumer.processMessage(record);

        verify(dailySummaryProcessor, never()).processTask(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(redisTemplate, never()).opsForStream();
    }
}
