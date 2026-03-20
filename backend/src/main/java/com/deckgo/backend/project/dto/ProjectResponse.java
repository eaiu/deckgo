package com.deckgo.backend.project.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String title,
    String topic,
    String audience,
    String templateId,
    UUID currentVersionId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
