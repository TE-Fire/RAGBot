package com.tefire.ai.robot.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import com.tefire.ai.robot.domain.dos.ChatDO;
import com.tefire.ai.robot.domain.mapper.ChatMapper;
import com.tefire.ai.robot.model.vo.chat.NewChatReqVO;
import com.tefire.ai.robot.model.vo.chat.NewChatRspVO;
import com.tefire.ai.robot.service.ChatService;
import com.tefire.ai.robot.utils.Response;
import com.tefire.ai.robot.utils.StringUtil;

import jakarta.annotation.Resource;

public class ChatServiceImpl implements ChatService {
    
    @Resource
    private ChatMapper chatMapper;

    @Override
    public Response<NewChatRspVO> newChat(NewChatReqVO newChatReqVO) {
        String message = newChatReqVO.getMessage();
        String uuid = UUID.randomUUID().toString();

        // 截断用户提问信息作为摘要
        String summary = StringUtil.truncate(message, 20);

        // 存储对话记录到数据库中
        chatMapper.insert(ChatDO.builder()
            .summary(summary)
            .uuid(uuid)
            .createTime(LocalDateTime.now())
            .updateTime(LocalDateTime.now())
            .build());

        return Response.success(NewChatRspVO.builder()
                    .uuid(uuid)
                    .summary(summary)
                    .build());
    }
}
