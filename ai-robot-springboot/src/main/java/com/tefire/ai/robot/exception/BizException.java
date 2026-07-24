package com.tefire.ai.robot.exception;

import lombok.Getter;
import lombok.Setter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-24 14:47:13
 * @Description: 业务异常
 */
@Getter
@Setter
public class BizException {
    // 异常码
    private String errorCode;
    // 错误信息
    private String errorMessage;

    public BizException(BaseExceptionInterface baseExceptionInterface) {
        this.errorCode = baseExceptionInterface.getErrorCode();
        this.errorMessage = baseExceptionInterface.getErrorMessage();
    }
}
