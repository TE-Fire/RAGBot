package com.tefire.airobot.config;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class McpStreamableHttpConfig {

    @Bean
    public NamedClientMcpTransport qqMcpServerTransport() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://localhost:8081")
                .endpoint("/mcp")
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        return new NamedClientMcpTransport("qq-mcp-server", transport);
    }
}
