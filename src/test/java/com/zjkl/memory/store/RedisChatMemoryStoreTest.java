package com.zjkl.memory.store;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.memory.constant.MemoryRedisKeys;
import com.zjkl.memory.service.SummaryMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private UserActivityTracker userActivityTracker;
    @Mock
    private SummaryMemoryService summaryMemoryService;

    private RedisChatMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new RedisChatMemoryStore(redisTemplate, userActivityTracker, summaryMemoryService);
    }

    @Test
    void deleteMessages_shouldDeleteDedupKeyTogetherWithMemoryKeys() {
        store.deleteMessages("u1");

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertEquals(List.of(
                MemoryRedisKeys.HISTORY_KEY + "u1",
                MemoryRedisKeys.SUMMARY_KEY + "u1",
                MemoryRedisKeys.LAST_COMPRESSED_SIZE_KEY + "u1",
                MemoryRedisKeys.SUMMARY_DEDUP_KEY + "u1"
        ), keysCaptor.getValue());
    }
}
