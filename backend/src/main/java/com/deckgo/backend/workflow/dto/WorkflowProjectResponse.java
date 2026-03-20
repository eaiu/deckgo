package com.deckgo.backend.workflow.dto;

import java.util.UUID;

public record WorkflowProjectResponse(
    UUID id,
    String title,
    String topic,
    String audience,
    String templateId
) {
}
