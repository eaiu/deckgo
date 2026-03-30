package com.deckgo.backend.project.controller;

import com.deckgo.backend.project.dto.RequirementAnswerPatchRequest;
import com.deckgo.backend.project.dto.RequirementAnswersBatchRequest;
import com.deckgo.backend.project.dto.RequirementConfirmRequest;
import com.deckgo.backend.project.service.ProjectRequirementService;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.dto.RequirementFormSnapshot;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/requirements")
public class ProjectRequirementController {

    private final ProjectRequirementService projectRequirementService;

    public ProjectRequirementController(ProjectRequirementService projectRequirementService) {
        this.projectRequirementService = projectRequirementService;
    }

    @GetMapping("/form")
    public RequirementFormSnapshot getRequirementForm(@PathVariable UUID projectId) {
        return projectRequirementService.getRequirementForm(projectId);
    }

    @PostMapping("/answers:batch")
    public RequirementFormSnapshot submitRequirementAnswers(
        @PathVariable UUID projectId,
        @Valid @RequestBody RequirementAnswersBatchRequest request
    ) {
        return projectRequirementService.submitRequirementAnswers(projectId, request);
    }

    @PatchMapping("/answers/{questionCode}")
    public RequirementFormSnapshot patchRequirementAnswer(
        @PathVariable UUID projectId,
        @PathVariable String questionCode,
        @RequestBody RequirementAnswerPatchRequest request
    ) {
        return projectRequirementService.patchRequirementAnswer(projectId, questionCode, request);
    }

    @PostMapping("/confirm")
    public ProjectStudioSnapshot confirmRequirements(
        @PathVariable UUID projectId,
        @RequestBody(required = false) RequirementConfirmRequest request
    ) {
        return projectRequirementService.confirmRequirements(
            projectId,
            request == null ? new RequirementConfirmRequest(null) : request
        );
    }
}
