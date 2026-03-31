package com.deckgo.backend.pptagent.dto;

import java.util.UUID;

public record PptBackgroundUploadResponse(
    UUID projectId,
    String backgroundAssetPath
) {
}
