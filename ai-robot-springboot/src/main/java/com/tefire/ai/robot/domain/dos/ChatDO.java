package com.tefire.ai.robot.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_chat")
public class ChatDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private String summary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
