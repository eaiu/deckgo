package com.deckgo.backend.pptagent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PptRequirementAnswersBatchRequest(
    @Valid @NotEmpty(message = "answers 不能为空") List<PptRequirementAnswerRequest> answers
) {
}
