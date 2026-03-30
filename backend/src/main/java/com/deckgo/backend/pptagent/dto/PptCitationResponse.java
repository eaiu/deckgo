package com.deckgo.backend.pptagent.dto;

public record PptCitationResponse(
    String title,
    String url,
    String excerptMd,
    String citationLabel
) {
}
