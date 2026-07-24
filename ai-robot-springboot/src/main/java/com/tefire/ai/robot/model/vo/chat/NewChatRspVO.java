/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-24 17:27:22
 * @Description: 
 */
package com.tefire.ai.robot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NewChatRspVO {
    
     /**
     * 摘要
     */
    private String summary;

    /**
     * 对话 UUID
     */
    private String uuid;
}
