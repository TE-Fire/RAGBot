package com.tefire.ai.robot.domain.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tefire.ai.robot.domain.dos.ChatDO;

@Mapper
public interface ChatMapper extends BaseMapper<ChatDO> {
    
}
