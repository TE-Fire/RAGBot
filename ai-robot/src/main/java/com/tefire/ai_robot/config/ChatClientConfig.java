package com.tefire.ai_robot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-09 09:23:29
 * @Description: ChatClient 客户端配置
 */
@Configuration
public class ChatClientConfig {

    @Resource
    private ChatMemory chatMemory;

    /**
     * 初始化 ChatClient 客户端
     * @param chatModel
     * @return
     */
    @Bean
    public ChatClient chatClient(DeepSeekChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(new SimpleLoggerAdvisor(), // 增添 Spring AI 内置的日志记录功能
                            // new MyLoggerAdvisor()) // 自定义 advisor
                            MessageChatMemoryAdvisor.builder(chatMemory).build() 
                    )
            .build();
    }
}
