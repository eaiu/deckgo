package com.deckgo.backend.render.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateRenderJobRequest(
    UUID deckVersionId,
    @NotBlank(message = "format 不能为空") String format
) {
}
