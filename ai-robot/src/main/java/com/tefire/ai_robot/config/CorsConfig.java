package com.tefire.ai_robot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-09 20:23:53
 * @Description: 跨域配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 匹配所以路径
        .allowedOriginPatterns("*") // 允许所有域名（生产环境应指定具体域名）
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许请求的方法
        .allowedHeaders("*") // 允许所有请求头
        .allowCredentials(true)
        .maxAge(3600); // 预检请求的有效期（秒）
    }
}
