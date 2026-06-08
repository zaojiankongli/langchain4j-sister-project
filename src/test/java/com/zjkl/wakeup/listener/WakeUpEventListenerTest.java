package com.zjkl.wakeup.listener;

import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.common.event.WakeUpSentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WakeUpEventListenerTest {

    @Mock
    private ConverMessageService converMessageService;

    @Test
    void onWakeUpSent_shouldPersistAssistantMessage() {
        WakeUpEventListener listener = new WakeUpEventListener(converMessageService);

        listener.onWakeUpSent(new WakeUpSentEvent("u1", "早上好呀", LocalDateTime.now()));

        verify(converMessageService).saveMessage(eq("u1"), eq("assistant"), anyList());
    }
}
