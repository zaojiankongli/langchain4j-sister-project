package com.zjkl.common.event;

import com.zjkl.anchor.model.AnchorEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 锚点触发事件 — 当情绪锚点被触发（INSERT）时发布
 */
@Data
@AllArgsConstructor
public class AnchorTriggeredEvent {

    private final String userId;
    private final AnchorEvent event;
    private final LocalDateTime timestamp;
}
