package com.deckgo.backend.project.pojo;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RequirementFormPO {

    private UUID id;
    private UUID projectId;
    private String status;
    private UUID basedOnOutlineVersionId;
    private String summaryMd;
    private String outlineContextMd;
    private String fixedItemsJson;
    private String initSearchQueriesJson;
    private String initSearchResultsJson;
    private String initCorpusDigestJson;
    private String aiQuestionsJson;
    private String answersJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getBasedOnOutlineVersionId() {
        return basedOnOutlineVersionId;
    }

    public void setBasedOnOutlineVersionId(UUID basedOnOutlineVersionId) {
        this.basedOnOutlineVersionId = basedOnOutlineVersionId;
    }

    public String getSummaryMd() {
        return summaryMd;
    }

    public void setSummaryMd(String summaryMd) {
        this.summaryMd = summaryMd;
    }

    public String getOutlineContextMd() {
        return outlineContextMd;
    }

    public void setOutlineContextMd(String outlineContextMd) {
        this.outlineContextMd = outlineContextMd;
    }

    public String getFixedItemsJson() {
        return fixedItemsJson;
    }

    public void setFixedItemsJson(String fixedItemsJson) {
        this.fixedItemsJson = fixedItemsJson;
    }

    public String getInitSearchQueriesJson() {
        return initSearchQueriesJson;
    }

    public void setInitSearchQueriesJson(String initSearchQueriesJson) {
        this.initSearchQueriesJson = initSearchQueriesJson;
    }

    public String getInitSearchResultsJson() {
        return initSearchResultsJson;
    }

    public void setInitSearchResultsJson(String initSearchResultsJson) {
        this.initSearchResultsJson = initSearchResultsJson;
    }

    public String getInitCorpusDigestJson() {
        return initCorpusDigestJson;
    }

    public void setInitCorpusDigestJson(String initCorpusDigestJson) {
        this.initCorpusDigestJson = initCorpusDigestJson;
    }

    public String getAiQuestionsJson() {
        return aiQuestionsJson;
    }

    public void setAiQuestionsJson(String aiQuestionsJson) {
        this.aiQuestionsJson = aiQuestionsJson;
    }

    public String getAnswersJson() {
        return answersJson;
    }

    public void setAnswersJson(String answersJson) {
        this.answersJson = answersJson;
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
