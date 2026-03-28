package com.deckgo.backend.project.controller;

import com.deckgo.backend.project.dto.ProjectResponse;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.studio.dto.CreateStudioProjectRequest;
import com.deckgo.backend.studio.dto.ProjectPageRedesignRequest;
import com.deckgo.backend.studio.dto.ProjectPageSnapshot;
import com.deckgo.backend.studio.dto.ProjectStudioChatRequest;
import com.deckgo.backend.studio.dto.ProjectStudioChatResponse;
import com.deckgo.backend.studio.dto.ProjectStudioCommandRequest;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.service.ProjectStudioService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectStudioService projectStudioService;

    public ProjectController(ProjectService projectService, ProjectStudioService projectStudioService) {
        this.projectService = projectService;
        this.projectStudioService = projectStudioService;
    }

    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/{projectId}")
    public ProjectStudioSnapshot getProject(@PathVariable UUID projectId) {
        return projectStudioService.getProject(projectId);
    }

    @PostMapping
    public ProjectStudioSnapshot createProject(@Valid @RequestBody CreateStudioProjectRequest request) {
        return projectStudioService.createProject(request);
    }

    @PostMapping("/{projectId}/commands")
    public ProjectStudioSnapshot executeCommand(
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectStudioCommandRequest request
    ) {
        return projectStudioService.executeCommand(projectId, request);
    }

    @PostMapping("/{projectId}/chat")
    public ProjectStudioChatResponse chat(
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectStudioChatRequest request
    ) {
        return projectStudioService.chat(projectId, request);
    }

    @GetMapping("/{projectId}/pages/{pageId}")
    public ProjectPageSnapshot getPage(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return projectStudioService.getPage(projectId, pageId);
    }

    @PostMapping("/{projectId}/pages/{pageId}/redesign")
    public ProjectPageSnapshot redesignPage(
        @PathVariable UUID projectId,
        @PathVariable UUID pageId,
        @RequestBody(required = false) ProjectPageRedesignRequest request
    ) {
        return projectStudioService.redesignPage(projectId, pageId, request == null ? null : request.instruction());
    }
}
