package com.zjkl.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 模型配置
 * 对应 application.yml 中 langchain4j.community.dashscope.* 及 wanx.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** DashScope Chat 模型 API 密钥 */
    @NotBlank
    private String chatApiKey;

    /** DashScope Chat 模型名称（默认：qwen3.5-flash） */
    @NotBlank
    private String chatModelName = "qwen3.5-flash";

    /** DashScope Vision 模型 API 密钥 */
    @NotBlank
    private String visionApiKey;

    /** DashScope Vision 模型名称（默认：qwen3-vl-flash） */
    @NotBlank
    private String visionModelName = "qwen3-vl-flash";

    /** 通义万相参考图片 URL */
    private String wanxReferenceImageUrl;

    /** Qwen-Omni-Realtime 模型名称（桌宠实时语音专用） */
    @NotBlank
    private String realtimeModelName = "qwen3.5-omni-plus-realtime";

    /** Qwen-Omni-Realtime WebSocket 地址（不含 model 查询参数） */
    @NotBlank
    private String realtimeUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";

    /** Qwen-Omni-Realtime 默认音色 */
    @NotBlank
    private String realtimeVoice = "Ethan";

}
