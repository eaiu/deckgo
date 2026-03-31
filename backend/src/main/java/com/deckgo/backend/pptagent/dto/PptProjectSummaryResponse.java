package com.deckgo.backend.pptagent.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PptProjectSummaryResponse(
    UUID projectId,
    String title,
    String requestText,
    String currentStage,
    String templateId,
    String previewSurface,
    String previewSvgMarkup,
    Integer pageCountTarget,
    String stylePreset,
    String backgroundAssetPath,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
