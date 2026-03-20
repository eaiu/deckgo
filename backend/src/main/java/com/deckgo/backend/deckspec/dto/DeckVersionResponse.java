package com.deckgo.backend.deckspec.dto;

import com.deckgo.backend.deckspec.enums.DeckVersionSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DeckVersionResponse(
    UUID id,
    UUID projectId,
    int versionNumber,
    DeckVersionSource source,
    String note,
    String templateId,
    String specTitle,
    int slideCount,
    JsonNode deckSpec,
    OffsetDateTime createdAt
) {
}
