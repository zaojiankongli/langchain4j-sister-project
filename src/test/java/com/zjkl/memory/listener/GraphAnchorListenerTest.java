package com.zjkl.memory.listener;

import com.zjkl.common.event.AnchorEndedEvent;
import com.zjkl.anchor.model.AnchorEvent;
import com.zjkl.memory.service.GraphEntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GraphAnchorListenerTest {

    @Mock
    private GraphEntityService graphEntityService;

    @Test
    void onAnchorEnded_shouldForwardAnchorEventIntoGraphMemory() {
        GraphAnchorListener listener = new GraphAnchorListener(graphEntityService);

        AnchorEvent event = AnchorEvent.builder()
                .id(42L)
                .userId("u1")
                .summary("图记忆要消费的锚点摘要")
                .eventTitle("锚点标题")
                .endType(AnchorEvent.EndType.POSITIVE)
                .endTime(LocalDateTime.now())
                .build();

        listener.onAnchorEnded(new AnchorEndedEvent("u1", event, event.getEndType(), LocalDateTime.now()));

        verify(graphEntityService).ingestAnchorEvent(event);
    }
}
