package com.zjkl.memory.listener;

import com.zjkl.common.event.AnchorEndedEvent;
import com.zjkl.memory.service.GraphEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GraphAnchorListener {

    private final GraphEntityService graphEntityService;

    @Async
    @EventListener
    public void onAnchorEnded(AnchorEndedEvent event) {
        try {
            graphEntityService.ingestAnchorEvent(event.getEvent());
        } catch (Exception e) {
            log.warn("图锚点消费失败 userId={}", event.getUserId(), e);
        }
    }
}
