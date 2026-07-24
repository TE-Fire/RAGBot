package com.tefire.server.config.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class QQTool {
    
    @Resource
    private RestTemplate restTemplate;

    @Value("${api-key}")
    private String apiKey;

    @Tool(description = "根据 QQ 号获取 QQ 信息")
    public String getQQInfo(String qq) {
        log.info("## 获取 QQ 信息, qq: {}", qq);

        // 请求第三方接口
        String url = String.format("http://api.guiguiya.com/api/qq_info?qq=%s&apiKey=%s", qq, apiKey);
        String result = restTemplate.getForObject(url, String.class);

        log.info("## 返参: {}", result);
        return result;
    }
}
