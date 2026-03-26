package com.deckgo.backend.workflow.entity;

import com.deckgo.backend.common.JsonSanitizer;
import com.deckgo.backend.workflow.enums.WorkflowMessageRole;
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "workflow_messages")
public class WorkflowMessageEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity = true;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStage stage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode contentJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls_json", columnDefinition = "jsonb")
    private JsonNode toolCallsJson;

    @Column(name = "message_type", nullable = false)
    private String messageType = "COMMAND";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.contentJson = JsonSanitizer.sanitize(this.contentJson);
        this.toolCallsJson = JsonSanitizer.sanitize(this.toolCallsJson);
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

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public WorkflowMessageRole getRole() {
        return role;
    }

    public void setRole(WorkflowMessageRole role) {
        this.role = role;
    }

    public WorkflowStage getStage() {
        return stage;
    }

    public void setStage(WorkflowStage stage) {
        this.stage = stage;
    }

    public JsonNode getContentJson() {
        return contentJson;
    }

    public void setContentJson(JsonNode contentJson) {
        this.contentJson = contentJson;
    }

    public JsonNode getToolCallsJson() {
        return toolCallsJson;
    }

    public void setToolCallsJson(JsonNode toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
