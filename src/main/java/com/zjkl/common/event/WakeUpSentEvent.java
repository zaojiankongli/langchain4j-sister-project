package com.zjkl.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 主动唤醒已发送事件 — 当唤醒文本成功送达用户后发布。
 */
@Data
@AllArgsConstructor
public class WakeUpSentEvent {

    private final String userId;
    private final String content;
    private final LocalDateTime timestamp;
}
