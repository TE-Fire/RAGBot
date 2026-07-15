package com.tefire.airobot.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.cassandra.CassandraChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-09 10:22:17
 * @Description: 记忆存储
 */
@Configuration
public class ChatMemoryConfig {
    
    /**
     * 记忆存储
     */
    // @Resource
    // private ChatMemoryRepository chatMemoryRepository;
    @Resource
    private CassandraChatMemoryRepository chatMemoryRepository;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(50) // // 最大消息窗口为 50，默认值为 20
                .chatMemoryRepository(chatMemoryRepository) // 记忆存储
                .build();
    }
}
