package com.deckgo.backend.project.pojo;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ProjectEventPO {

    private long streamId;
    private UUID eventId;
    private UUID projectId;
    private String eventType;
    private String stage;
    private String scopeType;
    private UUID targetPageId;
    private UUID agentRunId;
    private String payloadJson;
    private OffsetDateTime createdAt;

    public long getStreamId() {
        return streamId;
    }

    public void setStreamId(long streamId) {
        this.streamId = streamId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public UUID getTargetPageId() {
        return targetPageId;
    }

    public void setTargetPageId(UUID targetPageId) {
        this.targetPageId = targetPageId;
    }

    public UUID getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(UUID agentRunId) {
        this.agentRunId = agentRunId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
