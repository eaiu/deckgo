package com.deckgo.backend.project.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
    @NotBlank(message = "title 不能为空") String title,
    @NotBlank(message = "requestText 不能为空") String requestText
) {
}
