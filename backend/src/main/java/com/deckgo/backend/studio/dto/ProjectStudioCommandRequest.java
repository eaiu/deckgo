package com.deckgo.backend.studio.dto;

import com.deckgo.backend.studio.enums.WorkflowCommandType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ProjectStudioCommandRequest(
    @NotNull(message = "command 不能为空") WorkflowCommandType command,
    String scopeType,
    UUID targetPageId,
    List<String> selectedOptionIds,
    String freeformAnswer,
    String feedback
) {
}
