package com.zjkl.wakeup.listener;

import com.zjkl.ai.chat.entity.MessageContent;
import com.zjkl.ai.chat.service.ConverMessageService;
import com.zjkl.common.event.WakeUpSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 主动唤醒事件监听器 — 处理唤醒发送后的非关键副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WakeUpEventListener {

    private final ConverMessageService converMessageService;

    @Async
    @EventListener
    public void onWakeUpSent(WakeUpSentEvent event) {
        try {
            converMessageService.saveMessage(event.getUserId(), "assistant", List.of(MessageContent.text(event.getContent())));
        } catch (Exception e) {
            log.warn("保存唤醒消息到数据库失败（不影响发送）：userId={}", event.getUserId(), e);
        }
    }
}
