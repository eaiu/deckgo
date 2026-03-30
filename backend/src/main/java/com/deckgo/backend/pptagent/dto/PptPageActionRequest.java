package com.deckgo.backend.pptagent.dto;

public record PptPageActionRequest(
    String actionType,
    Boolean replaceExisting
) {
}
