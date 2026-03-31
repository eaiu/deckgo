package com.deckgo.backend.pptagent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PptExportJobResponse(
    UUID exportId,
    UUID projectId,
    String exportFormat,
    String status,
    String filePath,
    JsonNode resolvedManifest,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
