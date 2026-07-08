package com.tefire.ai_robot.controller;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import reactor.core.publisher.Flux;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-08 22:14:56
 * @Description: DeepSeek 聊天（推理大模型，输出思维链）
 */
@RestController
@RequestMapping("/v1/ai")
public class DeepSeekR1ChatController {
    
    @Resource
    private DeepSeekChatModel chatModel;

    @GetMapping(value = "/generateStream", produces = "text/html;charset=utf-8")
    public Flux<String> generateStream(@RequestParam(value = "message", defaultValue = "你是谁？") String message) {
        // 构建模型请求的选项对象，设置目标模型为 deepseek-v4-pro
        DeepSeekChatOptions chatOptions = DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue())
                .build();

        Prompt prompt = new Prompt(message, chatOptions);
        // 使用原子布尔值跟踪分隔线状态（每个请求独立）
        AtomicBoolean needSeparator = new AtomicBoolean(true);

        return chatModel.stream(prompt)
                .mapNotNull(chatResponse -> {
                    // 获取响应内容，强转为 DeepSeek 专属的消息对象，以便拿到推理内容
                    DeepSeekAssistantMessage deepSeekAssistantMessage = (DeepSeekAssistantMessage) chatResponse.getResult().getOutput();
                    // 获取推理内容
                    String reasoningContent = deepSeekAssistantMessage.getReasoningContent();
                    // 推理结束后的正式回答
                    String text = deepSeekAssistantMessage.getText();
                    // 是否是正式回答
                    boolean isTextResponse = false;
                    String rawContent;
                    if (Objects.isNull(text)) {
                        // text为null，说明还在推理
                        rawContent = reasoningContent;
                    } else {
                        // text 有值，说明推理结束
                        rawContent = text;
                        isTextResponse = true;
                    }

                    // 将 \n 替换为 HTML 换行标签 <br>，让浏览器能识别换行
                    String processed = StringUtils.isNotBlank(rawContent) ? rawContent.replace("\n", "<br>") : rawContent;

                    // 在正式回答内容之前，插入一条 <hr> 分割线，区分推理过程与正式回答
                    if (isTextResponse && needSeparator.compareAndSet(true, false)) {
                        processed = "<hr>" + processed;
                    }

                    return processed;
                });
        
    }

}
