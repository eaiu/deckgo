package com.deckgo.backend.workflow.entity;

import com.deckgo.backend.common.JsonSanitizer;
import com.deckgo.backend.workflow.enums.WorkflowSessionStatus;
import com.deckgo.backend.workflow.enums.WorkflowStage;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "workflow_sessions")
public class WorkflowSessionEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity = true;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    private WorkflowStage currentStage;

    @Column(name = "selected_template_id", nullable = false)
    private String selectedTemplateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "background_json", columnDefinition = "jsonb")
    private JsonNode backgroundJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discovery_json", columnDefinition = "jsonb")
    private JsonNode discoveryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discovery_answers_json", columnDefinition = "jsonb")
    private JsonNode discoveryAnswersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outline_json", columnDefinition = "jsonb")
    private JsonNode outlineJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "page_research_json", columnDefinition = "jsonb")
    private JsonNode pageResearchJson;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        sanitizeJsonFields();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        sanitizeJsonFields();
    }

    private void sanitizeJsonFields() {
        this.backgroundJson = JsonSanitizer.sanitize(this.backgroundJson);
        this.discoveryJson = JsonSanitizer.sanitize(this.discoveryJson);
        this.discoveryAnswersJson = JsonSanitizer.sanitize(this.discoveryAnswersJson);
        this.outlineJson = JsonSanitizer.sanitize(this.outlineJson);
        this.pageResearchJson = JsonSanitizer.sanitize(this.pageResearchJson);
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

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(UUID currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public WorkflowSessionStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowSessionStatus status) {
        this.status = status;
    }

    public WorkflowStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(WorkflowStage currentStage) {
        this.currentStage = currentStage;
    }

    public String getSelectedTemplateId() {
        return selectedTemplateId;
    }

    public void setSelectedTemplateId(String selectedTemplateId) {
        this.selectedTemplateId = selectedTemplateId;
    }

    public JsonNode getBackgroundJson() {
        return backgroundJson;
    }

    public void setBackgroundJson(JsonNode backgroundJson) {
        this.backgroundJson = backgroundJson;
    }

    public JsonNode getDiscoveryJson() {
        return discoveryJson;
    }

    public void setDiscoveryJson(JsonNode discoveryJson) {
        this.discoveryJson = discoveryJson;
    }

    public JsonNode getDiscoveryAnswersJson() {
        return discoveryAnswersJson;
    }

    public void setDiscoveryAnswersJson(JsonNode discoveryAnswersJson) {
        this.discoveryAnswersJson = discoveryAnswersJson;
    }

    public JsonNode getOutlineJson() {
        return outlineJson;
    }

    public void setOutlineJson(JsonNode outlineJson) {
        this.outlineJson = outlineJson;
    }

    public JsonNode getPageResearchJson() {
        return pageResearchJson;
    }

    public void setPageResearchJson(JsonNode pageResearchJson) {
        this.pageResearchJson = pageResearchJson;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
