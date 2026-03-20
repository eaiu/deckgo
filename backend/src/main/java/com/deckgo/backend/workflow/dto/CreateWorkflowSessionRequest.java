package com.deckgo.backend.workflow.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkflowSessionRequest(
    @NotBlank(message = "prompt 不能为空") String prompt
) {
}
