package com.deckgo.backend.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RequirementAnswersBatchRequest(
    @Valid @NotEmpty(message = "answers 不能为空") List<RequirementAnswerItemRequest> answers
) {
}
