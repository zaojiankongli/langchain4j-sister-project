package com.zjkl.memory.service;

import com.zjkl.common.config.properties.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptCacheServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PromptCacheService service;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        ReflectionTestUtils.setField(appProperties, "promptCacheTtl", 300);

        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new PromptCacheService(stringRedisTemplate, appProperties);
        service.init();
        clearInvocations(stringRedisTemplate, valueOperations);
    }

    @Test
    void getTemplate_shouldReuseLocalCacheAfterRedisHit() {
        when(valueOperations.get("prompt:template:redis-only")).thenReturn("cached prompt");

        assertEquals("cached prompt", service.getTemplate("redis-only"));
        assertEquals("cached prompt", service.getTemplate("redis-only"));

        verify(valueOperations, times(1)).get("prompt:template:redis-only");
    }

    @Test
    void getTemplate_shouldCoalesceConcurrentMissesForSameKey() throws Exception {
        int callers = 8;
        CountDownLatch redisLookupStarted = new CountDownLatch(1);
        CountDownLatch releaseRedisLookup = new CountDownLatch(1);
        when(valueOperations.get("prompt:template:redis-only")).thenAnswer(invocation -> {
            redisLookupStarted.countDown();
            releaseRedisLookup.await();
            return "cached prompt";
        });

        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                tasks.add(() -> service.getTemplate("redis-only"));
            }

            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(executor.submit(task));
            }

            redisLookupStarted.await();
            releaseRedisLookup.countDown();

            for (Future<String> future : futures) {
                assertEquals("cached prompt", future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        verify(valueOperations, times(1)).get("prompt:template:redis-only");
    }

    @Test
    void getTemplate_shouldThrowWhenTemplateMissing() {
        when(valueOperations.get("prompt:template:not-exists")).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getTemplate("not-exists")
        );

        assertEquals("未找到 Prompt 模板：not-exists", exception.getMessage());
        verify(valueOperations, times(1)).get("prompt:template:not-exists");
    }

    @Test
    void clearCache_shouldRemoveLocalAndRedisCache() {
        when(valueOperations.get("prompt:template:redis-only")).thenReturn("cached prompt");

        assertEquals("cached prompt", service.getTemplate("redis-only"));
        service.clearCache("redis-only");

        verify(stringRedisTemplate).delete("prompt:template:redis-only");
        verify(valueOperations, times(1)).get("prompt:template:redis-only");
    }
}
