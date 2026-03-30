package com.deckgo.backend.pptagent.dto;

import jakarta.validation.constraints.NotBlank;

public record PptProjectCreateRequest(
    String title,
    @NotBlank(message = "requestText 不能为空") String requestText
) {
}
