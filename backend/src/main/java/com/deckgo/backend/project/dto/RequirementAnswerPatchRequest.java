package com.deckgo.backend.project.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RequirementAnswerPatchRequest(
    JsonNode value
) {
}
