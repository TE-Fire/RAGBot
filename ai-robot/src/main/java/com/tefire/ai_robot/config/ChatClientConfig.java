package com.tefire.ai_robot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-09 09:23:29
 * @Description: ChatClient 客户端配置
 */
@Configuration
public class ChatClientConfig {

    /**
     * 初始化 ChatClient 客户端
     * @param chatModel
     * @return
     */
    @Bean
    public ChatClient chatClient(DeepSeekChatModel chatModel) {
        return ChatClient.builder(chatModel)
        .defaultSystem("扮演一名拼多多客服")
        .build();
    }
}
