package com.tefire.ai_robot.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import reactor.core.publisher.Flux;
import com.tefire.ai_robot.model.AIResponse;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-10 13:35:44
 * @Description: 提示词模板
 */
@RestController
@RequestMapping("/v4/ai")
public class PromptTemplateController {
    
    @Resource
    private DeepSeekChatModel chatModel;

    @Value("classpath:/prompts/code-assistant.st")
    private org.springframework.core.io.Resource templateResource;

    /**
     * 智能代码生成
     * @param message
     * @param lang
     * @return
     */
     @GetMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AIResponse> generateStream(@RequestParam(value = "message") String message,
                                           @RequestParam(value = "lang") String lang) {

        PromptTemplate promptTemplate = new PromptTemplate(templateResource);

        // 填充提示词占位符，转换为 Prompt 提示词对象
        Prompt prompt = promptTemplate.create(Map.of("description", message, "lang", lang));

        return chatModel.stream(prompt)
                .mapNotNull(chatResponse -> {
                    Generation generation = chatResponse.getResult();
                    String text = Objects.nonNull(generation) ? generation.getOutput().getText() : null;
                    if (text == null || text.isEmpty()) {
                        return null;
                    }
                    return AIResponse.builder().v(text).build();
                });
    }
}
