package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模型配置
 * 对应 application.yml 中 langchain4j.community.dashscope.* 及 wanx.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** DashScope Chat 模型 API 密钥 */
    private String chatApiKey;

    /** DashScope Chat 模型名称（默认：qwen3.5-flash） */
    private String chatModelName = "qwen3.5-flash";

    /** DashScope Vision 模型 API 密钥 */
    private String visionApiKey;

    /** DashScope Vision 模型名称（默认：qwen3-vl-flash） */
    private String visionModelName = "qwen3-vl-flash";

    /** 通义万相参考图片 URL */
    private String wanxReferenceImageUrl;

}
