package com.deckgo.backend.project.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
    @NotBlank(message = "title 不能为空") String title,
    @NotBlank(message = "topic 不能为空") String topic,
    @NotBlank(message = "audience 不能为空") String audience,
    @NotBlank(message = "templateId 不能为空") String templateId
) {
}
