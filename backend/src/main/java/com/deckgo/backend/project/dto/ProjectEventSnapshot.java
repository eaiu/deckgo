package com.deckgo.backend.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectEventSnapshot(
    long streamId,
    UUID eventId,
    UUID projectId,
    String eventType,
    String stage,
    String scopeType,
    UUID targetPageId,
    UUID agentRunId,
    JsonNode payload,
    OffsetDateTime createdAt
) {
}
