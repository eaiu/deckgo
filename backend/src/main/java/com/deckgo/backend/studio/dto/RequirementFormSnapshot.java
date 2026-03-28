package com.deckgo.backend.studio.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RequirementFormSnapshot(
    UUID id,
    String status,
    UUID basedOnOutlineVersionId,
    String summaryMd,
    String outlineContextMd,
    JsonNode fixedItems,
    JsonNode initSearchQueries,
    JsonNode initSearchResults,
    JsonNode initCorpusDigest,
    JsonNode aiQuestions,
    JsonNode answers,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
