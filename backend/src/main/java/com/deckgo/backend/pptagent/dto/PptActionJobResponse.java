package com.deckgo.backend.pptagent.dto;

import java.util.UUID;

public record PptActionJobResponse(
    String status,
    UUID agentRunId
) {
}
