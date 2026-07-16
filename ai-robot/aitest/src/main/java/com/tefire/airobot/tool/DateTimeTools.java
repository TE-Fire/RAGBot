package com.tefire.airobot.tool;

import java.time.LocalDateTime;

import org.springframework.ai.tool.annotation.Tool;

public class DateTimeTools {
    
    @Tool(description = "获取当前日期和时间")
    String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }
}
