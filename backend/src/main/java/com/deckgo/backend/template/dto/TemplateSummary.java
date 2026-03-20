package com.deckgo.backend.template.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record TemplateSummary(
    String id,
    String name,
    String description,
    List<String> slideKinds,
    JsonNode defaultTheme
) {
}
