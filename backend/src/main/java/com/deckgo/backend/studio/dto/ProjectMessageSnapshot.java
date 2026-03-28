package com.deckgo.backend.studio.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectMessageSnapshot(
    UUID id,
    String stage,
    String scopeType,
    UUID targetPageId,
    String role,
    String contentMd,
    JsonNode structuredPayload,
    OffsetDateTime createdAt
) {
}
