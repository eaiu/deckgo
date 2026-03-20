package com.deckgo.backend.artifact.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "artifacts")
public class ArtifactEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity = true;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "deck_version_id", nullable = false)
    private UUID deckVersionId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

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

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
