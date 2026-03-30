package com.deckgo.backend.pptagent.dto;

public record PptPageSearchResultResponse(
    String id,
    String queryText,
    String queryPurpose,
    Integer searchRank,
    String title,
    String url,
    String snippet,
    String contentExcerptMd,
    String readStatus,
    String vectorStatus,
    String sourceDocumentId
) {
}
