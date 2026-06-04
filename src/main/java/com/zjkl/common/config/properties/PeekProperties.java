package com.zjkl.common.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Peek（偷看/观察）配置
 * 对应 application.yml 中 peek.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.peek")
public class PeekProperties {

    /** 是否启用 Peek 功能（默认：true） */
    private boolean enabled = true;

    /** 基础概率（默认：0.08 = 8%） */
    @DecimalMin("0") @DecimalMax("1")
    private double baseProbability = 0.08;

    /** 概率上限（默认：0.25 = 25%） */
    @DecimalMin("0") @DecimalMax("1")
    private double maxProbability = 0.25;

    /** Peek 冷却时间（分钟），两次 peek 之间的最小间隔（默认：90） */
    @Min(0)
    private int cooldownMinutes = 90;

    /** 活跃阈值（分钟），最近 N 分钟有操作视为活跃（默认：5） */
    @Min(1)
    private int activeThresholdMinutes = 5;

    /** WakeUp 互斥时间（分钟），wakeup 后 N 分钟内不 peek（默认：10） */
    @Min(0)
    private int wakeupMutexMinutes = 10;

    /** 截图请求有效期（秒）（默认：120） */
    @Min(10)
    private int peekRequestTtlSeconds = 120;

    /** 最大并发请求数（默认：5） */
    @Min(1)
    private int maxConcurrentRequests = 5;

    /** OSS 截图存储目录（默认：peek） */
    private String screenshotFolder = "peek";

}
