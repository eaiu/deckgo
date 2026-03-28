package com.deckgo.backend.studio.dto;

import java.util.UUID;

public record ProjectStudioChatResponse(
    UUID projectId,
    String assistantMessage,
    ProjectStudioSnapshot snapshot
) {
}
