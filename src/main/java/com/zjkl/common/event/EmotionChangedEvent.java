package com.zjkl.common.event;

import com.zjkl.emotion.model.EmotionalState;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情绪变化事件 — 当用户的情绪状态发生改变时发布
 */
@Data
@AllArgsConstructor
public class EmotionChangedEvent {

    private final String userId;
    private final EmotionalState oldState;
    private final EmotionalState newState;
    private final LocalDateTime timestamp;
}
