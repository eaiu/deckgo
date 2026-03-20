package com.deckgo.backend.workflow.dto;

import com.deckgo.backend.workflow.enums.WorkflowCommandType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WorkflowCommandRequest(
    @NotNull(message = "command 不能为空") WorkflowCommandType command,
    List<String> selectedOptionIds,
    String freeformAnswer,
    String feedback
) {
}
