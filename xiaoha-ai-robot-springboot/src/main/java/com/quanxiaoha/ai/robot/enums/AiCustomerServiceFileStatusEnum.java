package com.quanxiaoha.ai.robot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: 犬小哈
 * @Date: 2026/8/22 13:00
 * @Version: v1.0.0
 * @Description: AI 客服 Markdown 文件状态
 **/
@Getter
@AllArgsConstructor
public enum AiCustomerServiceFileStatusEnum {

    UPLOADING(0, "上传中"),
    PENDING(1, "待处理"),
    VECTORIZING(2, "向量化中"),
    COMPLETED(3, "已完成"),
    FAILED(4, "失败");

    private Integer code;
    private String description;

    /**
     * 根据 code 获取枚举
     * @param code
     * @return
     */
    public static AiCustomerServiceFileStatusEnum codeOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (AiCustomerServiceFileStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

}
