package com.zjkl.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 服务配置
 * 对应 application.yml 中 mcp.* 的配置项
 */
@Data
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    /** Firecrawl MCP API 密钥 */
    private String firecrawlApiKey;

    /** Context7 MCP API 密钥 */
    private String context7ApiKey;

}
