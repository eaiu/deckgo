package com.deckgo.backend.pptagent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PptRequirementFormResponse(
    UUID projectId,
    String status,
    String summaryMd,
    JsonNode workflowConstraints,
    JsonNode initSearchQueries,
    JsonNode initSearchResults,
    JsonNode initCorpusDigest,
    JsonNode pageCountOptions,
    JsonNode fixedItems,
    JsonNode aiQuestions,
    List<PptRequirementQuestionResponse> questions,
    List<PptRequirementSourceResponse> sources,
    JsonNode answers,
    JsonNode suggestedActions,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
