package com.zjkl.ai.peek.tool;

import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.common.config.properties.PeekProperties;
import com.zjkl.wakeup.tool.TimeContextTool;
import com.zjkl.wakeup.tool.UserStateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeekStateToolTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private UserStateTool userStateTool;
    @Mock
    private UserActivityTracker userActivityTracker;

    private PeekProperties peekProperties;
    private PeekStateTool peekStateTool;

    @BeforeEach
    void setUp() {
        peekProperties = new PeekProperties();
        peekStateTool = new PeekStateTool(redisTemplate, userStateTool, userActivityTracker, peekProperties);
    }

    @Test
    void getContinuousActiveMinutes_shouldUseSessionStartInsteadOfLastActivity() {
        long now = System.currentTimeMillis();
        when(userActivityTracker.getSessionStartTime("u1")).thenReturn(now - java.time.Duration.ofMinutes(70).toMillis());

        int result = peekStateTool.getContinuousActiveMinutes("u1");

        assertTrue(result >= 70);
    }
}
