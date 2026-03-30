package com.deckgo.backend.pptagent.dto;

import java.util.List;
import java.util.UUID;

public record PptStoryboardPagePatchRequest(
    UUID pageId,
    String title,
    List<String> contentOutline
) {
}
