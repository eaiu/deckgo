package com.deckgo.backend.pptagent.dto;

import java.util.List;

public record PptStoryboardPatchRequest(
    List<PptStoryboardSectionPatchRequest> parts
) {
}
