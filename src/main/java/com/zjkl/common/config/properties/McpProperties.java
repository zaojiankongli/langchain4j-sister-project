package com.zjkl.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MCP 服务配置
 * 对应 application.yml 中 mcp.* 的配置项
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    /** Firecrawl MCP API 密钥 */
    @NotBlank
    private String firecrawlApiKey;

    /** Context7 MCP API 密钥 */
    @NotBlank
    private String context7ApiKey;

}
