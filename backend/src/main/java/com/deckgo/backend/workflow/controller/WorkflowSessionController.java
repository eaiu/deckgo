package com.deckgo.backend.workflow.controller;

import com.deckgo.backend.workflow.dto.CreateWorkflowSessionRequest;
import com.deckgo.backend.workflow.dto.WorkflowCommandRequest;
import com.deckgo.backend.workflow.dto.WorkflowSessionResponse;
import com.deckgo.backend.workflow.service.WorkflowSessionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-sessions")
public class WorkflowSessionController {

    private final WorkflowSessionService workflowSessionService;

    public WorkflowSessionController(WorkflowSessionService workflowSessionService) {
        this.workflowSessionService = workflowSessionService;
    }

    @PostMapping
    public WorkflowSessionResponse create(@Valid @RequestBody CreateWorkflowSessionRequest request) {
        return workflowSessionService.createSession(request);
    }

    @GetMapping("/{sessionId}")
    public WorkflowSessionResponse get(@PathVariable UUID sessionId) {
        return workflowSessionService.getSession(sessionId);
    }

    @PostMapping("/{sessionId}/commands")
    public WorkflowSessionResponse command(@PathVariable UUID sessionId, @Valid @RequestBody WorkflowCommandRequest request) {
        return workflowSessionService.executeCommand(sessionId, request);
    }
}
