package com.deckgo.backend.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record WorkflowPageResponse(
    UUID id,
    int orderIndex,
    String title,
    JsonNode pagePlan,
    String draftSvg,
    String finalSvg
) {
}
