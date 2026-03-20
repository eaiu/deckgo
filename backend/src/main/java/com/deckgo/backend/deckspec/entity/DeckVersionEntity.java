package com.deckgo.backend.deckspec.entity;

import com.deckgo.backend.deckspec.enums.DeckVersionSource;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "deck_versions")
public class DeckVersionEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity = true;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeckVersionSource source;

    @Column(length = 1000)
    private String note;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "spec_title", nullable = false)
    private String specTitle;

    @Column(name = "slide_count", nullable = false)
    private Integer slideCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spec_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode specJson;

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

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public DeckVersionSource getSource() {
        return source;
    }

    public void setSource(DeckVersionSource source) {
        this.source = source;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getSpecTitle() {
        return specTitle;
    }

    public void setSpecTitle(String specTitle) {
        this.specTitle = specTitle;
    }

    public Integer getSlideCount() {
        return slideCount;
    }

    public void setSlideCount(Integer slideCount) {
        this.slideCount = slideCount;
    }

    public JsonNode getSpecJson() {
        return specJson;
    }

    public void setSpecJson(JsonNode specJson) {
        this.specJson = specJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
