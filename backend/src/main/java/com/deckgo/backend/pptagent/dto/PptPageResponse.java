package com.deckgo.backend.pptagent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PptPageResponse(
    UUID pageId,
    UUID projectId,
    String pageCode,
    String pageRole,
    String partTitle,
    Integer sortOrder,
    String title,
    List<String> contentOutline,
    String outlineStatus,
    String searchStatus,
    String summaryStatus,
    String draftStatus,
    String designStatus,
    List<PptPageSearchQueryResponse> pageSearchQueries,
    List<PptPageSearchResultResponse> pageSearchResults,
    PptCorpusDigestResponse pageCorpusDigest,
    String pageSummaryMd,
    List<PptCitationResponse> pageSummaryCitations,
    JsonNode currentArtifactStaleness,
    UUID currentBriefVersionId,
    UUID currentDraftVersionId,
    UUID currentDesignVersionId,
    String draftPreviewSvgMarkup,
    String designPreviewSvgMarkup,
    String previewSurface,
    String previewSvgMarkup,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
