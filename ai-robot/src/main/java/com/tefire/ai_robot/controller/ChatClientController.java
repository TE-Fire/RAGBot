package com.tefire.ai_robot.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import reactor.core.publisher.Flux;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-09 09:27:02
 * @Description: 测试
 */
@RestController
@RequestMapping("/v2/ai")
public class ChatClientController {
    
    @Resource
    private ChatClient chatClient;

    @GetMapping("/generate")
    public String generate(@RequestParam(value = "message", defaultValue = "你是谁？") String message) {

        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

      /**
     * 流式对话
     * @param message
     * @return
     */
    @GetMapping(value = "/generateStream", produces = "text/html;charset=utf-8")
    public Flux<String> generateStream(@RequestParam(value = "message", defaultValue = "你是谁？") String message,
                                        @RequestParam(value = "chatId") String chatId) {
        return chatClient.prompt()
                .user(message) // 提示词
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)) // 对话 id
                .stream() // 流式输出
                .content();

    }
}
