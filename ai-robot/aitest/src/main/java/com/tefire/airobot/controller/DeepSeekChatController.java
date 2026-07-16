package com.tefire.airobot.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.airobot.model.AIResponse;

import jakarta.annotation.Resource;
import reactor.core.publisher.Flux;
/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-08 21:52:19
 * @Description: 测试
 */
@RestController
@RequestMapping("/v1/ai")
public class DeepSeekChatController {
    
    private Map<String, List<Message>> chatMemoryStore = new ConcurrentHashMap<>();
    
    @Resource
    private DeepSeekChatModel deepSeekChatModel;


    @GetMapping("/generate")
    public String generate(@RequestParam(value = "message", defaultValue = "你是谁？") String message,
                            @RequestParam(value = "chatId") String chatId) {
        // 根据 chatId 获取对话记录
        List<Message> messages = chatMemoryStore.get(chatId);
        if (CollectionUtils.isEmpty(messages)) {
            messages = new ArrayList<>();
            chatMemoryStore.put(chatId, messages);
        }
        // 添加 “用户角色消息” 到聊天记录中
        messages.add(new UserMessage(message));

        Prompt prompt = new Prompt(message);
        String responseText = deepSeekChatModel.call(prompt).getResult().getOutput().getText();
        // 添加 “助手角色消息” 到聊天记录中
        messages.add(new AssistantMessage(responseText));

        // 一次性返回结果
        return responseText;
    }

    @GetMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AIResponse> generateStream(@RequestParam(value = "message", defaultValue = "你是谁？") String message) {
        // 构建提示词
        Prompt prompt = new Prompt(new UserMessage(message));

        return deepSeekChatModel.stream(prompt)
                .mapNotNull(chatResponse -> {
                    Generation generation = chatResponse.getResult();
                    String text = generation.getOutput().getText();
                    if (text == null || text.isEmpty()) {
                        return null;
                    }
                    return AIResponse.builder().v(text).build();
                });
    }
}
