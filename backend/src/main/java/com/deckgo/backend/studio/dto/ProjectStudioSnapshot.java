package com.deckgo.backend.studio.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectStudioSnapshot(
    UUID projectId,
    String title,
    String topic,
    String audience,
    String templateId,
    String requestText,
    String currentStage,
    UUID currentOutlineVersionId,
    Integer pageCountTarget,
    String stylePreset,
    String backgroundAssetPath,
    JsonNode workflowConstraints,
    RequirementFormSnapshot requirementForm,
    OutlineVersionSnapshot currentOutline,
    List<ProjectPageSnapshot> pages,
    List<ProjectMessageSnapshot> messages,
    List<StageRunSnapshot> projectRuns,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
