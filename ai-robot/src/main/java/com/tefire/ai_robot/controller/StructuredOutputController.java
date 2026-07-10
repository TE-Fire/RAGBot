package com.tefire.ai_robot.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.ai_robot.model.ActorFilmography;

import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-10 14:20:51
 * @Description: 格式化输出
 */
@RestController
@RequestMapping("/v5/ai")
public class StructuredOutputController {
    
    @Resource
    private ChatClient chatClient;

     /**
     * 示例1: BeanOutputConverter - 获取演员电影作品集
     * @param name
     * @return
     */
    @GetMapping("/actor/films")
    public ActorFilmography generate(@RequestParam(value = "name") String name,
                                     @RequestParam(value = "chatId") String chatId) {

        return chatClient.prompt()
                    .user(u -> u.text("""
                                请为演员 {actor} 生成包含5部代表作的电影作品集,
                                只包含 {actor} 担任主演的电影，不要包含任何解释说明。
                                """)
                    .param("actor", name))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .entity(ActorFilmography.class);
    }
}
