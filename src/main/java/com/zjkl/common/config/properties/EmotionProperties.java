package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 情绪引擎配置
 * 对应 application.yml 中 emotion.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.emotion")
public class EmotionProperties {

    /** 锚点事件最大持续时长（分钟），超过此时间自动结束（默认：60） */
    private int anchorMaxDurationMinutes = 60;

}
