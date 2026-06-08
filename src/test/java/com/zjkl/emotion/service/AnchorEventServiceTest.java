package com.zjkl.emotion.service;

import com.zjkl.common.config.properties.EmotionProperties;
import com.zjkl.common.event.EmotionChangedEvent;
import com.zjkl.anchor.monitor.AnchorMonitor;
import com.zjkl.anchor.service.AnchorEventService;
import com.zjkl.anchor.service.AnchorSemanticService;
import com.zjkl.anchor.mapper.AnchorMapper;
import com.zjkl.emotion.model.EmotionalState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnchorEventServiceTest {

    @Mock
    private AnchorMapper anchorMapper;
    @Mock
    private AnchorMonitor anchorMonitor;
    @Mock
    private AnchorSemanticService semanticService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EmotionProperties emotionProperties;

    @InjectMocks
    private AnchorEventService anchorEventService;

    @Test
    void onEmotionChanged_shouldOnlyForwardToMonitorWithoutRepublishingEmotionEvent() {
        EmotionChangedEvent event = new EmotionChangedEvent(
                "user-1",
                new EmotionalState(0.1, 0.2, 0.3),
                new EmotionalState(0.4, 0.2, 0.3),
                LocalDateTime.now()
        );

        anchorEventService.onEmotionChanged(event);

        verify(anchorMonitor).onEmotionChange(event.getUserId(), event.getOldState(), event.getNewState());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
