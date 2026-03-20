package com.deckgo.backend.artifact.service;

import com.deckgo.backend.artifact.entity.ArtifactEntity;
import com.deckgo.backend.artifact.repository.ArtifactRepository;
import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.common.exception.NotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final Path artifactsDir;

    public ArtifactService(ArtifactRepository artifactRepository, DeckGoProperties properties) {
        this.artifactRepository = artifactRepository;
        this.artifactsDir = Path.of(properties.getArtifactsDir()).normalize();
    }

    @Transactional(readOnly = true)
    public ArtifactEntity getArtifact(UUID artifactId) {
        return artifactRepository.findById(artifactId)
            .orElseThrow(() -> new NotFoundException("导出产物不存在: " + artifactId));
    }

    public Resource loadAsResource(ArtifactEntity artifact) {
        return new FileSystemResource(Path.of(artifact.getStoragePath()));
    }

    public ArtifactEntity createArtifact(UUID projectId, UUID deckVersionId, UUID jobId) {
        try {
            Files.createDirectories(artifactsDir);
        } catch (IOException exception) {
            throw new IllegalStateException("创建产物目录失败: " + artifactsDir, exception);
        }

        ArtifactEntity entity = new ArtifactEntity();
        entity.setId(UUID.randomUUID());
        entity.setProjectId(projectId);
        entity.setDeckVersionId(deckVersionId);
        entity.setFilename(jobId + ".pptx");
        entity.setMediaType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        entity.setStoragePath(artifactsDir.resolve(entity.getFilename()).toString());
        return artifactRepository.save(entity);
    }

    public void refreshSize(ArtifactEntity artifact) {
        try {
            Path path = Path.of(artifact.getStoragePath());
            if (Files.exists(path)) {
                artifact.setSizeBytes(Files.size(path));
                artifactRepository.save(artifact);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取导出文件大小失败: " + artifact.getStoragePath(), exception);
        }
    }
}
