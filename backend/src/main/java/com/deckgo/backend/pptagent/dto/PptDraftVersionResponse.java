package com.deckgo.backend.pptagent.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PptDraftVersionResponse(
    UUID draftVersionId,
    UUID projectId,
    UUID pageId,
    Integer versionNo,
    String status,
    UUID pageBriefVersionId,
    UUID researchSessionId,
    String draftSvgMarkup,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
