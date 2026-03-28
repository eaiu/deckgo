package com.deckgo.backend.studio.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OutlineVersionSnapshot(
    UUID id,
    Integer versionNo,
    String status,
    UUID parentVersionId,
    JsonNode outline,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
