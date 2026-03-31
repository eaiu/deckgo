package com.deckgo.backend.pptagent.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PptRequirementQuestionOptionInput(
    String optionCode,
    String label,
    String description,
    JsonNode value
) {
}
