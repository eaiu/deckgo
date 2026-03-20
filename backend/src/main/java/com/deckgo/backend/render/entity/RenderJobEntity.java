package com.deckgo.backend.render.entity;

import com.deckgo.backend.render.enums.RenderJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "render_jobs")
public class RenderJobEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity = true;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "deck_version_id", nullable = false)
    private UUID deckVersionId;

    @Column(nullable = false)
    private String format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RenderJobStatus status;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PostPersist
    @PostLoad
    public void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getDeckVersionId() {
        return deckVersionId;
    }

    public void setDeckVersionId(UUID deckVersionId) {
        this.deckVersionId = deckVersionId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public RenderJobStatus getStatus() {
        return status;
    }

    public void setStatus(RenderJobStatus status) {
        this.status = status;
    }

    public UUID getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(UUID artifactId) {
        this.artifactId = artifactId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
