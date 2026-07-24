package com.tefire.ai.robot.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"actor", "movies"})
public record ActorFilmography(String actor, List<String> movies) {
}
