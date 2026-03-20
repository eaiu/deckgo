package com.deckgo.backend.workflow.dto;

import com.deckgo.backend.workflow.enums.WorkflowSessionStatus;
import com.deckgo.backend.workflow.enums.WorkflowStage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowSessionResponse(
    UUID sessionId,
    WorkflowStage currentStage,
    WorkflowSessionStatus status,
    UUID currentVersionId,
    String selectedTemplateId,
    String lastError,
    WorkflowProjectResponse project,
    List<WorkflowMessageResponse> messages,
    JsonNode discoveryCard,
    JsonNode researchSummary,
    JsonNode outline,
    List<WorkflowPageResponse> pages,
    OffsetDateTime updatedAt
) {
}
