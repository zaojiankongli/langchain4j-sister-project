package com.zjkl.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TTS 语音合成配置
 * 对应 application.yml 中 tts.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.tts")
public class TtsProperties {

    /** TTS 模型名称（默认：cosyvoice-v3.5-flash） */
    @NotBlank
    private String model = "cosyvoice-v3.5-flash";

    /** TTS 发音人 */
    @NotBlank
    private String voice;

}
