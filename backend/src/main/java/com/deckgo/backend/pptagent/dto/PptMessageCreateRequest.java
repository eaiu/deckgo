package com.deckgo.backend.pptagent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PptMessageCreateRequest(
    String scopeType,
    UUID targetPageId,
    String uiSurface,
    @NotBlank(message = "contentMd 不能为空") String contentMd,
    List<Map<String, Object>> attachments
) {
}
