package com.zjkl.recommendation.mcp;

import com.zjkl.common.config.properties.McpProperties;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceMcpClientTest {

    @Mock
    private McpProperties mcpProperties;

    @InjectMocks
    private ResourceMcpClient resourceMcpClient;

    @Test
    void testFirecrawlMcpClientCreation() {
        // Arrange
        when(mcpProperties.getFirecrawlApiKey()).thenReturn("test-firecrawl-key");
        ResourceMcpClient spy = spy(resourceMcpClient);
        McpClient mockClient = mock(McpClient.class);
        when(mockClient.key()).thenReturn("FIRECRAWL_MCP_CLIENT");
        doReturn(mockClient).when(spy).buildClient(eq("FIRECRAWL_MCP_CLIENT"), any(McpTransport.class));

        // Act
        McpClient firecrawlMcpClient = spy.firecrawlMcpClient();

        // Assert
        assertNotNull(firecrawlMcpClient);
        assertEquals("FIRECRAWL_MCP_CLIENT", firecrawlMcpClient.key());
    }

    @Test
    void testContext7McpClientCreation() {
        // Arrange
        when(mcpProperties.getContext7ApiKey()).thenReturn("test-context7-key");
        ResourceMcpClient spy = spy(resourceMcpClient);
        McpClient mockClient = mock(McpClient.class);
        when(mockClient.key()).thenReturn("CONTEXT7_MCP_CLIENT");
        doReturn(mockClient).when(spy).buildClient(eq("CONTEXT7_MCP_CLIENT"), any(McpTransport.class));

        // Act
        McpClient context7McpClient = spy.context7McpClient();

        // Assert
        assertNotNull(context7McpClient);
        assertEquals("CONTEXT7_MCP_CLIENT", context7McpClient.key());
    }

    @Test
    void testMcpToolProviderCreation() {
        // Arrange
        McpClient context7McpClient = mock(McpClient.class);
        McpClient firecrawlMcpClient = mock(McpClient.class);

        // Act
        McpToolProvider mcpToolProvider = resourceMcpClient.mcpToolProvider(
                context7McpClient, firecrawlMcpClient);

        // Assert
        assertNotNull(mcpToolProvider);
    }

    @Test
    void testConfigurationClass() {
        ResourceMcpClient client = new ResourceMcpClient(mcpProperties);
        assertNotNull(client);
    }
}
