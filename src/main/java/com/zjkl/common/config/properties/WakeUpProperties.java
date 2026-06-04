package com.zjkl.common.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 主动唤醒配置
 * 对应 application.yml 中 wake-up.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.wake-up")
public class WakeUpProperties {

    /** 是否启用主动唤醒（默认：true） */
    private boolean enabled = true;

    /** 用户沉默阈值（分钟），超过此时长视为可唤醒（默认：90） */
    @Min(1)
    private int silentThresholdMinutes = 90;

    /** 唤醒冷却时间（分钟），两次唤醒之间的最小间隔（默认：30） */
    @Min(0)
    private int cooldownMinutes = 30;

    /** 唤醒概率 Sigmoid 中点（默认：2.5） */
    @Positive
    private double probabilityMidpoint = 2.5;

    /** 唤醒概率 Sigmoid 陡度（默认：1.0） */
    @Positive
    private double probabilitySteepness = 1.0;

    /** 唤醒概率上限（默认：0.75） */
    @DecimalMin("0") @DecimalMax("1")
    private double probabilityMax = 0.75;

}
