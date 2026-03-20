package com.deckgo.backend.render.dto;

import com.deckgo.backend.render.enums.RenderJobStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RenderJobResponse(
    UUID id,
    UUID projectId,
    UUID deckVersionId,
    String format,
    RenderJobStatus status,
    UUID artifactId,
    String errorMessage,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt
) {
}
