package com.deckgo.backend.workflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "workflow_pages")
public class WorkflowPageEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity = true;

    @Column(name = "workflow_version_id", nullable = false)
    private UUID workflowVersionId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "page_plan_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode pagePlanJson;

    @Column(name = "draft_svg", columnDefinition = "text")
    private String draftSvg;

    @Column(name = "final_svg", columnDefinition = "text")
    private String finalSvg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
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

    public UUID getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(UUID workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public JsonNode getPagePlanJson() {
        return pagePlanJson;
    }

    public void setPagePlanJson(JsonNode pagePlanJson) {
        this.pagePlanJson = pagePlanJson;
    }

    public String getDraftSvg() {
        return draftSvg;
    }

    public void setDraftSvg(String draftSvg) {
        this.draftSvg = draftSvg;
    }

    public String getFinalSvg() {
        return finalSvg;
    }

    public void setFinalSvg(String finalSvg) {
        this.finalSvg = finalSvg;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
