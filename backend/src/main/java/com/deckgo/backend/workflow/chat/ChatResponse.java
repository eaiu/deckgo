package com.deckgo.backend.workflow.chat;

import com.deckgo.backend.workflow.dto.WorkflowSessionResponse;
import java.util.List;
import java.util.UUID;

public record ChatResponse(
    UUID sessionId,
    String assistantMessage,
    List<ToolCallStep> toolCalls,
    WorkflowSessionResponse sessionSnapshot
) {}
