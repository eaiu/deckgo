package com.deckgo.backend.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectDetailResponse(
    UUID id,
    String title,
    String topic,
    String audience,
    String templateId,
    String requestText,
    String currentStage,
    UUID currentOutlineVersionId,
    Integer pageCountTarget,
    String stylePreset,
    String backgroundAssetPath,
    JsonNode workflowConstraints,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
