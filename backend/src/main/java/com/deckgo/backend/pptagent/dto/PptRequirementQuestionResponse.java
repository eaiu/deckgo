package com.deckgo.backend.pptagent.dto;

import java.util.List;

public record PptRequirementQuestionResponse(
    String questionCode,
    String label,
    String description,
    List<PptRequirementQuestionOptionResponse> options
) {
}
