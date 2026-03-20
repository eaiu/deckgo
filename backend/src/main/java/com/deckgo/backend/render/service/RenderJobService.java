package com.deckgo.backend.render.service;

import com.deckgo.backend.artifact.entity.ArtifactEntity;
import com.deckgo.backend.artifact.service.ArtifactService;
import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.deckspec.entity.DeckVersionEntity;
import com.deckgo.backend.deckspec.service.DeckVersionService;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.render.dto.CreateRenderJobRequest;
import com.deckgo.backend.render.dto.RenderJobResponse;
import com.deckgo.backend.render.entity.RenderJobEntity;
import com.deckgo.backend.render.enums.RenderJobStatus;
import com.deckgo.backend.render.repository.RenderJobRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenderJobService {

    private final RenderJobRepository renderJobRepository;
    private final ProjectService projectService;
    private final DeckVersionService deckVersionService;
    private final ArtifactService artifactService;

    public RenderJobService(
        RenderJobRepository renderJobRepository,
        ProjectService projectService,
        DeckVersionService deckVersionService,
        ArtifactService artifactService
    ) {
        this.renderJobRepository = renderJobRepository;
        this.projectService = projectService;
        this.deckVersionService = deckVersionService;
        this.artifactService = artifactService;
    }

    @Transactional
    public RenderJobResponse createJob(UUID projectId, CreateRenderJobRequest request) {
        ProjectEntity project = projectService.findEntity(projectId);
        UUID targetVersionId = request.deckVersionId() != null ? request.deckVersionId() : project.getCurrentVersionId();
        if (targetVersionId == null) {
            throw new NotFoundException("项目还没有可用的当前版本: " + projectId);
        }

        DeckVersionEntity deckVersion = deckVersionService.findEntity(targetVersionId);
        if (!deckVersion.getProjectId().equals(project.getId())) {
            throw new NotFoundException("版本不属于当前项目: " + targetVersionId);
        }

        RenderJobEntity entity = new RenderJobEntity();
        entity.setId(UUID.randomUUID());
        entity.setProjectId(projectId);
        entity.setDeckVersionId(deckVersion.getId());
        entity.setFormat(request.format());
        entity.setStatus(RenderJobStatus.QUEUED);
        renderJobRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public RenderJobResponse getJob(UUID jobId) {
        return toResponse(findEntity(jobId));
    }

    @Transactional(readOnly = true)
    public RenderJobEntity findEntity(UUID jobId) {
        return renderJobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("渲染任务不存在: " + jobId));
    }

    @Transactional
    public Optional<RenderJobEntity> acquireNextQueuedJob() {
        return renderJobRepository.findTopByStatusOrderByCreatedAtAsc(RenderJobStatus.QUEUED)
            .map(job -> {
                job.setStatus(RenderJobStatus.RUNNING);
                job.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
                return renderJobRepository.save(job);
            });
    }

    @Transactional
    public void markSucceeded(RenderJobEntity job, ArtifactEntity artifact) {
        job.setStatus(RenderJobStatus.SUCCEEDED);
        job.setArtifactId(artifact.getId());
        job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        renderJobRepository.save(job);
    }

    @Transactional
    public void markFailed(RenderJobEntity job, String errorMessage) {
        job.setStatus(RenderJobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        renderJobRepository.save(job);
    }

    @Transactional
    public ArtifactEntity createArtifactForJob(RenderJobEntity job) {
        return artifactService.createArtifact(job.getProjectId(), job.getDeckVersionId(), job.getId());
    }

    public RenderJobResponse toResponse(RenderJobEntity entity) {
        return new RenderJobResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getDeckVersionId(),
            entity.getFormat(),
            entity.getStatus(),
            entity.getArtifactId(),
            entity.getErrorMessage(),
            entity.getCreatedAt(),
            entity.getStartedAt(),
            entity.getCompletedAt()
        );
    }
}
