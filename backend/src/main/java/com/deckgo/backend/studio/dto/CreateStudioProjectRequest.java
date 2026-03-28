package com.deckgo.backend.studio.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record CreateStudioProjectRequest(
    @NotBlank(message = "prompt 不能为空") String prompt,
    Integer pageCountTarget,
    String stylePreset,
    String backgroundAssetPath,
    JsonNode workflowConstraints
) {
}
