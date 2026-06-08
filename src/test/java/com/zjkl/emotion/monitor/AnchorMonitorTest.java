package com.zjkl.emotion.monitor;

import com.zjkl.common.config.properties.EmotionProperties;
import com.zjkl.anchor.monitor.AnchorMonitor;
import com.zjkl.emotion.model.EmotionalState;
import com.zjkl.anchor.model.AnchorEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnchorMonitorTest {

    @Mock
    private EmotionProperties emotionProperties;

    private AnchorMonitor monitor;

    @BeforeEach
    void setUp() {
        when(emotionProperties.getAnchorMaxDurationMinutes()).thenReturn(60);
        monitor = new AnchorMonitor(emotionProperties);
    }

    @Test
    void onEmotionChange_shouldRefreshLastMsgTimeWhileMonitoringSoActiveUserIsNotEndedAsSilent() {
        AtomicBoolean ended = new AtomicBoolean(false);
        monitor.setOnEnd(event -> ended.set(true));

        monitor.onEmotionChange("u1", new EmotionalState(0.0, 0.1, 0.0), new EmotionalState(0.3, 0.2, 0.0));

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> monitors = (ConcurrentHashMap<String, Object>) ReflectionTestUtils.getField(monitor, "monitors");
        Object state = monitors.get("u1");
        ReflectionTestUtils.setField(state, "lastMsgTime", LocalDateTime.now().minusHours(3));

        monitor.onEmotionChange("u1", new EmotionalState(0.3, 0.2, 0.0), new EmotionalState(0.35, 0.25, 0.0));

        assertFalse(ended.get());
    }
}
