package com.zjkl.ai.chat.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HeartbeatCheckerTest {

    @Mock
    private ConnectionStateManager connectionStateManager;

    private HeartbeatChecker heartbeatChecker;

    @BeforeEach
    void setUp() {
        heartbeatChecker = new HeartbeatChecker(connectionStateManager);
    }

    @Test
    void checkHeartbeats_shouldRemoveTimedOutUserAfterDisconnect() {
        heartbeatChecker.updateActiveTime("u1");

        @SuppressWarnings("unchecked")
        Map<String, Long> lastActiveTime = (Map<String, Long>) ReflectionTestUtils.getField(heartbeatChecker, "lastActiveTime");
        lastActiveTime.put("u1", System.currentTimeMillis() - 91_000);

        heartbeatChecker.checkHeartbeats();
        heartbeatChecker.checkHeartbeats();

        verify(connectionStateManager, times(1)).onUserDisconnected("u1");
        assertFalse(lastActiveTime.containsKey("u1"));
    }
}
