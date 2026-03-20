package com.deckgo.backend.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record AiDeckProposalResponse(
    UUID proposalId,
    String mode,
    JsonNode deckSpec,
    String summary,
    List<String> validationMessages
) {
}
