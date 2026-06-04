package com.zjkl.ai.chat.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatPushServiceImplTest {

    @Mock
    private ConnectionStateManager connectionStateManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private HeartbeatChecker heartbeatChecker;
    private ChatPushServiceImpl chatPushService;

    @BeforeEach
    void setUp() {
        heartbeatChecker = new HeartbeatChecker(eventPublisher);
        chatPushService = new ChatPushServiceImpl(connectionStateManager, heartbeatChecker);
    }

    @Test
    void onUserDisconnected_shouldClearHeartbeatTracking() {
        heartbeatChecker.updateActiveTime("u1");

        chatPushService.onUserDisconnected("u1");

        @SuppressWarnings("unchecked")
        Map<String, Long> lastActiveTime = (Map<String, Long>) ReflectionTestUtils.getField(heartbeatChecker, "lastActiveTime");

        verify(connectionStateManager).onUserDisconnected("u1");
        // HeartbeatChecker now publishes events instead of calling connectionStateManager directly
        // ChatPushServiceImpl.onUserDisconnected still clears heartbeat tracking
        assertFalse(lastActiveTime.containsKey("u1"));
    }

    @Test
    void pushPetExpression_shouldDelegateToConnectionStateManager() {
        chatPushService.pushPetExpression("u1", "happy", 0.8, 3000);

        verify(connectionStateManager).pushPetExpression("u1", "happy", 0.8, 3000);
    }

    @Test
    void pushPetMotion_shouldDelegateToConnectionStateManager() {
        chatPushService.pushPetMotion("u1", "thinking", "normal");

        verify(connectionStateManager).pushPetMotion("u1", "thinking", "normal");
    }
}
