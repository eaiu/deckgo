package com.deckgo.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDeckRevisionRequest(
    @NotNull(message = "projectId 不能为空") UUID projectId,
    @NotNull(message = "baseVersionId 不能为空") UUID baseVersionId,
    @NotBlank(message = "instruction 不能为空") String instruction
) {
}
