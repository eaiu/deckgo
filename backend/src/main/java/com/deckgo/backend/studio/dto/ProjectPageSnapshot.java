package com.deckgo.backend.studio.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectPageSnapshot(
    UUID id,
    String pageCode,
    String pageRole,
    String partTitle,
    Integer sortOrder,
    UUID currentBriefVersionId,
    UUID currentResearchSessionId,
    UUID currentDraftVersionId,
    UUID currentDesignVersionId,
    String outlineStatus,
    String searchStatus,
    String summaryStatus,
    String draftStatus,
    String designStatus,
    JsonNode artifactStaleness,
    JsonNode currentBrief,
    JsonNode currentResearch,
    String currentDraftSvg,
    String currentDesignSvg,
    List<JsonNode> citations,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
