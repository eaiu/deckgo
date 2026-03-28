package com.deckgo.backend.studio.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectStudioChatRequest(
    @NotBlank(message = "message 不能为空") String message
) {
}
