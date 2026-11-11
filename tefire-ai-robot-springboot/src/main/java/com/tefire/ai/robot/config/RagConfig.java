package com.tefire.ai.robot.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: TE
 * @Date: 2026/10/8 13:49
 * @Version: v1.0.0
 * @Description: RAG 检索优化相关配置类
 **/
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    /**
     * Token 切分器：将超长的文档块按 Token 数切小，避免一个块里混杂多个主题、稀释语义
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter(RagProperties ragProperties) {
        return TokenTextSplitter.builder()
                .withChunkSize(ragProperties.getSplitter().getChunkSize()) // 每块目标 Token 数
                .withMinChunkSizeChars(ragProperties.getSplitter().getMinChunkSizeChars()) // 标点截断的最小字符数
                .build();
    }

}
