package com.deckgo.backend.project.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String title,
    String topic,
    String requestText,
    String currentStage,
    Integer pageCountTarget,
    String stylePreset,
    String backgroundAssetPath,
    String audience,
    String templateId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
