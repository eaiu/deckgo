package com.deckgo.backend.studio.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StageRunSnapshot(
    UUID id,
    String stage,
    Integer attemptNo,
    String status,
    JsonNode inputRefs,
    JsonNode outputRef,
    String errorMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt
) {
}
