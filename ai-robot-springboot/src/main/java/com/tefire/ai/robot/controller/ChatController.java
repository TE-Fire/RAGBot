package com.tefire.ai.robot.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.ai.robot.aspect.ApiOperationLog;
import com.tefire.ai.robot.model.vo.chat.AIResponse;
import com.tefire.ai.robot.model.vo.chat.AiChatReqVO;
import com.tefire.ai.robot.model.vo.chat.NewChatReqVO;
import com.tefire.ai.robot.service.ChatService;
import com.tefire.ai.robot.utils.Response;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
@Slf4j
public class ChatController {
    
    @Resource
    private ChatService chatService;

    @PostMapping("/new")
    @ApiOperationLog(description = "新建对话")
    public Response<?> newChat(@RequestBody @Validated NewChatReqVO newChatReqVO) {
        return chatService.newChat(newChatReqVO);
    }

    @PostMapping(value = "/completion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperationLog(description = "流式对话")
    public Flux<AIResponse> chat(@RequestBody @Validated AiChatReqVO aiChatReqVO) {
        // 用户消息
        String userMessage = aiChatReqVO.getMessage();
        // 模型名称
        String modelName = aiChatReqVO.getModelName();
        // 温度值
        Double temperature = aiChatReqVO.getTemperature();

        // 构建 ChatModel (Spring AI 2.0 正确方式)
        ChatModel chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .build())
                .build();

        // 流式输出
        return ChatClient.create(chatModel)
                .prompt()
                .user(userMessage)
                .stream()
                .content()
                .mapNotNull(text -> {
                    if (StringUtils.isAllBlank(text)) {
                        return null;
                    }
                    return AIResponse.builder().v(text).build();
                });
    }
}
