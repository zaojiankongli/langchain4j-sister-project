package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 主动唤醒配置
 * 对应 application.yml 中 wake-up.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.wake-up")
public class WakeUpProperties {

    /** 是否启用主动唤醒（默认：true） */
    private boolean enabled = true;

    /** 用户沉默阈值（分钟），超过此时长视为可唤醒（默认：90） */
    private int silentThresholdMinutes = 90;

    /** 唤醒冷却时间（分钟），两次唤醒之间的最小间隔（默认：30） */
    private int cooldownMinutes = 30;

    /** 唤醒概率 Sigmoid 中点（默认：2.5） */
    private double probabilityMidpoint = 2.5;

    /** 唤醒概率 Sigmoid 陡度（默认：1.0） */
    private double probabilitySteepness = 1.0;

    /** 唤醒概率上限（默认：0.75） */
    private double probabilityMax = 0.75;

}
