package com.deckgo.backend.workflow.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    @NotBlank(message = "消息不能为空") String message
) {}
