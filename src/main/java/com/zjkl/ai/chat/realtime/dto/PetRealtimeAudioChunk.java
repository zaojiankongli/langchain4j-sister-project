package com.zjkl.ai.chat.realtime.dto;

import lombok.Data;

/**
 * 桌宠前端推送给后端的 16 kHz mono PCM 音频分片。
 */
@Data
public class PetRealtimeAudioChunk {

    /** Base64 编码的 PCM16LE 音频数据。 */
    private String audioBase64;

    /** 客户端采集时间戳，仅用于调试/追踪。 */
    private Long timestamp;
}
