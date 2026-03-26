package com.deckgo.backend.workflow.chat;

import java.time.Instant;
import java.util.List;

public record ToolProgressEvent(
    String toolName,
    String status,
    String description,
    Instant timestamp,
    List<ToolSubStep> subSteps
) {}
