package com.deckgo.backend.pptagent.dto;

import java.util.List;

public record PptStoryboardSectionPatchRequest(
    String partTitle,
    List<PptStoryboardPagePatchRequest> pages
) {
}
