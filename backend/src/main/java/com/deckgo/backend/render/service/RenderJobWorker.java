package com.deckgo.backend.render.service;

import com.deckgo.backend.artifact.entity.ArtifactEntity;
import com.deckgo.backend.artifact.service.ArtifactService;
import com.deckgo.backend.deckspec.entity.DeckVersionEntity;
import com.deckgo.backend.deckspec.repository.DeckVersionRepository;
import com.deckgo.backend.render.entity.RenderJobEntity;
import java.nio.file.Path;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RenderJobWorker {

    private final RenderJobService renderJobService;
    private final DeckVersionRepository deckVersionRepository;
    private final RendererClient rendererClient;
    private final ArtifactService artifactService;

    public RenderJobWorker(
        RenderJobService renderJobService,
        DeckVersionRepository deckVersionRepository,
        RendererClient rendererClient,
        ArtifactService artifactService
    ) {
        this.renderJobService = renderJobService;
        this.deckVersionRepository = deckVersionRepository;
        this.rendererClient = rendererClient;
        this.artifactService = artifactService;
    }

    @Scheduled(fixedDelayString = "${deckgo.render-worker-delay-ms:5000}")
    public void consumeQueuedJobs() {
        renderJobService.acquireNextQueuedJob().ifPresent(this::process);
    }

    private void process(RenderJobEntity job) {
        try {
            DeckVersionEntity deckVersion = deckVersionRepository.findById(job.getDeckVersionId())
                .orElseThrow(() -> new IllegalStateException("渲染任务关联的版本不存在: " + job.getDeckVersionId()));

            ArtifactEntity artifact = renderJobService.createArtifactForJob(job);
            Path outputPath = Path.of(artifact.getStoragePath()).toAbsolutePath().normalize();

            rendererClient.render(new RendererClient.RendererRequest(
                job.getId().toString(),
                deckVersion.getTemplateId(),
                deckVersion.getSpecJson(),
                outputPath.toString()
            ));

            artifactService.refreshSize(artifact);
            renderJobService.markSucceeded(job, artifact);
        } catch (Exception exception) {
            renderJobService.markFailed(job, exception.getMessage());
        }
    }
}
