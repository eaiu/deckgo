package com.deckgo.backend.pptagent.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PptDesignVersionResponse(
    UUID designVersionId,
    UUID projectId,
    UUID pageId,
    Integer versionNo,
    String status,
    UUID draftVersionId,
    String stylePackId,
    String backgroundAssetPath,
    String designSvgMarkup,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
