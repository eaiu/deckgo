package com.deckgo.backend.project.pojo;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ProjectPO {

    private UUID id;
    private String title;
    private String topic;
    private String audience;
    private String templateId;
    private String requestText;
    private String currentStage;
    private UUID currentOutlineVersionId;
    private Integer pageCountTarget;
    private String stylePreset;
    private String backgroundAssetPath;
    private String workflowConstraintsJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getRequestText() {
        return requestText;
    }

    public void setRequestText(String requestText) {
        this.requestText = requestText;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public UUID getCurrentOutlineVersionId() {
        return currentOutlineVersionId;
    }

    public void setCurrentOutlineVersionId(UUID currentOutlineVersionId) {
        this.currentOutlineVersionId = currentOutlineVersionId;
    }

    public Integer getPageCountTarget() {
        return pageCountTarget;
    }

    public void setPageCountTarget(Integer pageCountTarget) {
        this.pageCountTarget = pageCountTarget;
    }

    public String getStylePreset() {
        return stylePreset;
    }

    public void setStylePreset(String stylePreset) {
        this.stylePreset = stylePreset;
    }

    public String getBackgroundAssetPath() {
        return backgroundAssetPath;
    }

    public void setBackgroundAssetPath(String backgroundAssetPath) {
        this.backgroundAssetPath = backgroundAssetPath;
    }

    public String getWorkflowConstraintsJson() {
        return workflowConstraintsJson;
    }

    public void setWorkflowConstraintsJson(String workflowConstraintsJson) {
        this.workflowConstraintsJson = workflowConstraintsJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
