package com.deckgo.backend.deckspec.dto;

import com.deckgo.backend.deckspec.enums.DeckVersionSource;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record CreateDeckVersionRequest(
    @NotNull(message = "deckSpec 不能为空") JsonNode deckSpec,
    String note,
    @NotNull(message = "source 不能为空") DeckVersionSource source
) {
}
