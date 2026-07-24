package com.tefire.airobot.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyLoggerAdvisor implements CallAdvisor {
    
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.info("## 请求入参: {}", chatClientRequest);
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        log.info("## 请求出参: {}", chatClientResponse);
        
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 1; // order 值越小，越先执行
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName(); // 获取类名
    }
}
