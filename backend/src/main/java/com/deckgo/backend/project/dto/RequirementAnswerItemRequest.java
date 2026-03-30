package com.deckgo.backend.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record RequirementAnswerItemRequest(
    @NotBlank(message = "questionCode 不能为空") String questionCode,
    JsonNode value
) {
}
