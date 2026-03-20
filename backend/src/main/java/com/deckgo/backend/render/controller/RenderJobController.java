package com.deckgo.backend.render.controller;

import com.deckgo.backend.render.dto.CreateRenderJobRequest;
import com.deckgo.backend.render.dto.RenderJobResponse;
import com.deckgo.backend.render.service.RenderJobService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RenderJobController {

    private final RenderJobService renderJobService;

    public RenderJobController(RenderJobService renderJobService) {
        this.renderJobService = renderJobService;
    }

    @PostMapping("/api/projects/{projectId}/render-jobs")
    public RenderJobResponse createJob(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateRenderJobRequest request
    ) {
        return renderJobService.createJob(projectId, request);
    }

    @GetMapping("/api/render-jobs/{jobId}")
    public RenderJobResponse getJob(@PathVariable UUID jobId) {
        return renderJobService.getJob(jobId);
    }
}
