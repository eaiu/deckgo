package com.deckgo.backend.project.service.impl;

import com.deckgo.backend.project.dto.ProjectEventSnapshot;
import com.deckgo.backend.project.mapper.ProjectEventMapper;
import com.deckgo.backend.project.pojo.ProjectEventPO;
import com.deckgo.backend.project.service.ProjectEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectEventServiceImpl implements ProjectEventService {

    private final ProjectEventMapper projectEventMapper;
    private final ObjectMapper objectMapper;

    public ProjectEventServiceImpl(ProjectEventMapper projectEventMapper, ObjectMapper objectMapper) {
        this.projectEventMapper = projectEventMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectEventSnapshot> listEventsAfter(UUID projectId, long afterStreamId, int limit) {
        return projectEventMapper.selectEventsAfterStreamId(projectId, afterStreamId, limit).stream()
            .map(this::toSnapshot)
            .toList();
    }

    private ProjectEventSnapshot toSnapshot(ProjectEventPO event) {
        return new ProjectEventSnapshot(
            event.getStreamId(),
            event.getEventId(),
            event.getProjectId(),
            event.getEventType(),
            event.getStage(),
            event.getScopeType(),
            event.getTargetPageId(),
            event.getAgentRunId(),
            readJson(event.getPayloadJson()),
            event.getCreatedAt()
        );
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("解析 project event payload 失败", exception);
        }
    }
}
