package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TTS 语音合成配置
 * 对应 application.yml 中 tts.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.tts")
public class TtsProperties {

    /** TTS 模型名称（默认：cosyvoice-v3.5-flash） */
    private String model = "cosyvoice-v3.5-flash";

    /** TTS 发音人 */
    private String voice;

}
