package com.tefire;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.tefire.server.config.tool.QQTool;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerQqApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerQqApplication.class, args);
    }

    /**
     * 注册工具回调（Tool Callbacks）
     * @param qqTool
     * @return
     */
    @Bean
    public ToolCallbackProvider qqTools(QQTool qqTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(qqTool)
                .build();
    }
}
