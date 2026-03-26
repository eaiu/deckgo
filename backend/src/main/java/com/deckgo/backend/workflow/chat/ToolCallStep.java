package com.deckgo.backend.workflow.chat;

import java.util.List;

public record ToolCallStep(
    String id,
    String toolName,
    String displayName,
    String status,
    long durationMs,
    String summary,
    List<ToolSubStep> subSteps
) {}
