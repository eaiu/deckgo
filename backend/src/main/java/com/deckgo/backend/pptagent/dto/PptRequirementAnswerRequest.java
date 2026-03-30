package com.deckgo.backend.pptagent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record PptRequirementAnswerRequest(
    @NotBlank(message = "questionCode 不能为空") String questionCode,
    JsonNode value
) {
}
