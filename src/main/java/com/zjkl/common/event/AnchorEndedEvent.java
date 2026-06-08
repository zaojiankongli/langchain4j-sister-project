package com.zjkl.common.event;

import com.zjkl.anchor.model.AnchorEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 锚点结束事件 — 当情绪锚点结束（UPDATE）时发布
 */
@Data
@AllArgsConstructor
public class AnchorEndedEvent {

    private final String userId;
    private final AnchorEvent event;
    private final AnchorEvent.EndType endType;
    private final LocalDateTime timestamp;
}
