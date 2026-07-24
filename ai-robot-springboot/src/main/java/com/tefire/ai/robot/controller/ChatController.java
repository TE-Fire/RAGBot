package com.tefire.ai.robot.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.tefire.ai.robot.aspect.ApiOperationLog;
import com.tefire.ai.robot.model.vo.chat.NewChatReqVO;
import com.tefire.ai.robot.service.ChatService;
import com.tefire.ai.robot.utils.Response;

import jakarta.annotation.Resource;

public class ChatController {
    
    @Resource
    private ChatService chatService;

    @PostMapping("/new")
    @ApiOperationLog(description = "新建对话")
    public Response<?> newChat(@RequestBody @Validated NewChatReqVO newChatReqVO) {
        return chatService.newChat(newChatReqVO);
    }
}
