package com.deckgo.backend.workflow.dto;

import com.deckgo.backend.workflow.enums.WorkflowMessageRole;
import com.deckgo.backend.workflow.enums.WorkflowStage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkflowMessageResponse(
    UUID id,
    WorkflowMessageRole role,
    WorkflowStage stage,
    JsonNode content,
    JsonNode toolCalls,
    String messageType,
    OffsetDateTime createdAt
) {
}
