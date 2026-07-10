package com.tefire.ai_robot.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-10 14:18:57
 * @Description: 演员 - 电影集合
 */
@JsonPropertyOrder({"actor", "movies"})
public record ActorFilmography(String actor, List<String> movies) {
    
}
