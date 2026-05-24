package com.zjkl.recommendation.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 资源推荐专用 MCP 客户端配置
 */
@Configuration
public class ResourceMcpClient {

    @Value("${mcp.firecrawl.api-key}")
    private String firecrawlApiKey;

    @Value("${mcp.context7.api-key}")
    private String context7ApiKey;

    @Bean
    public McpClient firecrawlMcpClient() {
        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url("https://mcp.firecrawl.dev/" + firecrawlApiKey + "/v2/mcp")
                .logRequests(false)
                .logResponses(false)
                .build();

        return DefaultMcpClient.builder()
                .key("FIRECRAWL_MCP_CLIENT")
                .transport(transport)
                .build();
    }

    @Bean
    public McpClient context7McpClient() {
        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url("https://mcp.context7.com/mcp")
                .customHeaders(Map.of(
                        "Authorization", "Bearer " + context7ApiKey,
                        "Content-Type", "application/json"
                ))
                .logRequests(false)
                .logResponses(false)
                .build();

        return DefaultMcpClient.builder()
                .key("CONTEXT7_MCP_CLIENT")
                .transport(transport)
                .build();
    }

    @Bean
    public McpToolProvider mcpToolProvider(
            @Qualifier("context7McpClient") McpClient context7McpClient,
            @Qualifier("firecrawlMcpClient") McpClient firecrawlMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(List.of(context7McpClient, firecrawlMcpClient))
                .build();
    }
}
