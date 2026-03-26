package com.deckgo.backend.workflow.chat;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-sessions")
public class ChatController {

    private final OrchestratorService orchestratorService;

    public ChatController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/{sessionId}/chat")
    public ChatResponse chat(
        @PathVariable UUID sessionId,
        @Valid @RequestBody ChatRequest request
    ) {
        return orchestratorService.chat(sessionId, request);
    }
}
