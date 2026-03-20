package com.deckgo.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDeckDraftRequest(
    @NotBlank(message = "topic 不能为空") String topic,
    @NotBlank(message = "audience 不能为空") String audience,
    @NotBlank(message = "goal 不能为空") String goal,
    @NotBlank(message = "templateId 不能为空") String templateId,
    Integer slideCountHint
) {
}
