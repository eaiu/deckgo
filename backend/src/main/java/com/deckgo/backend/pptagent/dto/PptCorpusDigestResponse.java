package com.deckgo.backend.pptagent.dto;

public record PptCorpusDigestResponse(
    String collectionId,
    Integer documentCount,
    Integer chunkCount,
    String latestDocumentTitle,
    String updatedAt
) {
}
