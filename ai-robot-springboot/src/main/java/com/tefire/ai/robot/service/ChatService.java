package com.tefire.ai.robot.service;

import com.tefire.ai.robot.model.vo.chat.NewChatReqVO;
import com.tefire.ai.robot.model.vo.chat.NewChatRspVO;
import com.tefire.ai.robot.utils.Response;

public interface ChatService {
    
    /**
     * 新建对话
     * @param newChatReqVO
     * @return
     */
    Response<NewChatRspVO> newChat(NewChatReqVO newChatReqVO);
}
