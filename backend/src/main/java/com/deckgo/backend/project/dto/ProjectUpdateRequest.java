package com.deckgo.backend.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProjectUpdateRequest(
    @NotBlank(message = "title 不能为空") String title,
    @NotBlank(message = "topic 不能为空") String topic,
    @NotBlank(message = "audience 不能为空") String audience,
    String templateId,
    String requestText,
    @Positive(message = "pageCountTarget 必须大于 0") Integer pageCountTarget,
    String stylePreset,
    String backgroundAssetPath,
    JsonNode workflowConstraints
) {
}
