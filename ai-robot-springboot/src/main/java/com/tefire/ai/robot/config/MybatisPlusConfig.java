package com.tefire.ai.robot.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tefire.ai.robot.domain.mapper")
public class MybatisPlusConfig {
    
}
