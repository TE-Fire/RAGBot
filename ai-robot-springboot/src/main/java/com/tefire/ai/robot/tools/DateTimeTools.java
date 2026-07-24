package com.tefire.ai.robot.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;

@Slf4j
public class DateTimeTools {

    @Tool(description = "获取当前日期和时间")
    String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }

}
