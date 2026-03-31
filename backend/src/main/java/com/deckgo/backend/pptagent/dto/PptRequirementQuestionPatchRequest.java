package com.deckgo.backend.pptagent.dto;

import java.util.List;

public record PptRequirementQuestionPatchRequest(
    String label,
    String description,
    List<PptRequirementQuestionOptionInput> options,
    Boolean allowCustom
) {
}
