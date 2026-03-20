package com.deckgo.backend.workflow.entity;

import com.deckgo.backend.workflow.enums.WorkflowVersionSource;
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
@Table(name = "workflow_versions")
public class WorkflowVersionEntity implements Persistable<UUID> {

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
    private WorkflowVersionSource source;

    @Column(length = 1000)
    private String note;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "background_json", columnDefinition = "jsonb")
    private JsonNode backgroundJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "research_json", columnDefinition = "jsonb")
    private JsonNode pageResearchJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outline_json", columnDefinition = "jsonb")
    private JsonNode outlineJson;

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

    public WorkflowVersionSource getSource() {
        return source;
    }

    public void setSource(WorkflowVersionSource source) {
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

    public JsonNode getBackgroundJson() {
        return backgroundJson;
    }

    public void setBackgroundJson(JsonNode backgroundJson) {
        this.backgroundJson = backgroundJson;
    }

    public JsonNode getPageResearchJson() {
        return pageResearchJson;
    }

    public void setPageResearchJson(JsonNode pageResearchJson) {
        this.pageResearchJson = pageResearchJson;
    }

    public JsonNode getOutlineJson() {
        return outlineJson;
    }

    public void setOutlineJson(JsonNode outlineJson) {
        this.outlineJson = outlineJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
