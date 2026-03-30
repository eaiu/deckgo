package com.deckgo.backend.pptagent.dto;

import jakarta.validation.constraints.NotBlank;

public record PptSummaryPatchRequest(
    @NotBlank(message = "summaryMd 不能为空") String summaryMd
) {
}
