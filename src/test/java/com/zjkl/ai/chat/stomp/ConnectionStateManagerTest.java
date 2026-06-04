package com.zjkl.ai.chat.stomp;

import com.zjkl.common.config.properties.ThreadPoolProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionStateManagerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageQueueManager queueManager;

    @Mock
    private ConnectionStateRegistry stateRegistry;

    @Mock
    private ThreadPoolProperties threadPoolProperties;

    private ConnectionStateManager connectionStateManager;

    @BeforeEach
    void setUp() {
        when(threadPoolProperties.getWebsocketSenderCoreSize()).thenReturn(1);
        connectionStateManager = new ConnectionStateManager(
                messagingTemplate,
                queueManager,
                stateRegistry,
                threadPoolProperties
        );
    }

    @Test
    void onUserDisconnected_shouldCleanupChatAndControlQueues() {
        when(stateRegistry.isConnected("u1")).thenReturn(false);

        connectionStateManager.onUserDisconnected("u1");

        verify(queueManager, timeout(7000)).clearAndRemoveQueue("u1");
        verify(queueManager, timeout(7000)).clearAndRemoveQueue("u1" + MessageQueueManager.CONTROL_SUFFIX);
    }

    @Test
    void onUserDisconnected_shouldRemoveDisconnectedUserStateAfterCleanup() {
        when(stateRegistry.isConnected("u1")).thenReturn(false);

        connectionStateManager.onUserDisconnected("u1");

        verify(stateRegistry, timeout(7000)).removeUser("u1");
    }
}
