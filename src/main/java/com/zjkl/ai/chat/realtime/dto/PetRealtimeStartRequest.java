package com.zjkl.ai.chat.realtime.dto;

import lombok.Data;

/**
 * 桌宠 Qwen-Omni-Realtime 会话启动请求。
 */
@Data
public class PetRealtimeStartRequest {

    /** 输出音色；为空时使用后端默认配置。 */
    private String voice;

    /** semantic_vad 阈值。 */
    private Double threshold;

    /** 语音停止静音时长。 */
    private Integer silenceDurationMs;

}
