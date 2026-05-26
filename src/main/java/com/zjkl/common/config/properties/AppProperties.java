package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用通用配置
 * 对应 application.yml 中 app.* 及其他零散配置项
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 默认图片 URL（图片生成失败时降级使用） */
    private String defaultImageUrl;

    /** 推荐系统演示用户 ID（默认：demo_user） */
    private String recommendationDemoUser = "demo_user";

    /** Prompt 缓存 TTL（秒）（默认：300） */
    private int promptCacheTtl = 300;

}
