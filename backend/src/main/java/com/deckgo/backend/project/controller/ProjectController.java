package com.deckgo.backend.project.controller;

import com.deckgo.backend.project.dto.ProjectCreateRequest;
import com.deckgo.backend.project.dto.ProjectDetailResponse;
import com.deckgo.backend.project.dto.ProjectResponse;
import com.deckgo.backend.project.dto.ProjectUpdateRequest;
import com.deckgo.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/{projectId}")
    public ProjectDetailResponse getProject(@PathVariable UUID projectId) {
        return projectService.getProject(projectId);
    }

    @PostMapping
    public ProjectDetailResponse createProject(@Valid @RequestBody ProjectCreateRequest request) {
        return projectService.createProject(request);
    }

    @PutMapping("/{projectId}")
    public ProjectDetailResponse updateProject(@PathVariable UUID projectId, @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.updateProject(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    public void deleteProject(@PathVariable UUID projectId) {
        projectService.deleteProject(projectId);
    }
}
