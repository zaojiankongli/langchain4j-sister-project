package com.zjkl.ai.chat.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HeartbeatCheckerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private HeartbeatChecker heartbeatChecker;

    @BeforeEach
    void setUp() {
        heartbeatChecker = new HeartbeatChecker(eventPublisher);
    }

    @Test
    void checkHeartbeats_shouldPublishDisconnectEventAfterTimeout() {
        heartbeatChecker.updateActiveTime("u1");

        @SuppressWarnings("unchecked")
        Map<String, Long> lastActiveTime = (Map<String, Long>) ReflectionTestUtils.getField(heartbeatChecker, "lastActiveTime");
        lastActiveTime.put("u1", System.currentTimeMillis() - 91_000);

        heartbeatChecker.checkHeartbeats();

        ArgumentCaptor<HeartbeatChecker.UserDisconnectedEvent> captor =
            ArgumentCaptor.forClass(HeartbeatChecker.UserDisconnectedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals("u1", captor.getValue().userId());
        assertFalse(lastActiveTime.containsKey("u1"));
    }
}
