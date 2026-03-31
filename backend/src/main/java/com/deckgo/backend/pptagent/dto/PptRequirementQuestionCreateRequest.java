package com.deckgo.backend.pptagent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PptRequirementQuestionCreateRequest(
    @NotBlank(message = "questionCode 不能为空") String questionCode,
    @NotBlank(message = "label 不能为空") String label,
    String description,
    List<PptRequirementQuestionOptionInput> options,
    Boolean allowCustom
) {
}
