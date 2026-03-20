package com.deckgo.backend.deckspec.service;

import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.deckspec.dto.CreateDeckVersionRequest;
import com.deckgo.backend.deckspec.dto.DeckVersionResponse;
import com.deckgo.backend.deckspec.entity.DeckVersionEntity;
import com.deckgo.backend.deckspec.enums.DeckVersionSource;
import com.deckgo.backend.deckspec.repository.DeckVersionRepository;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeckVersionService {

    private final DeckVersionRepository deckVersionRepository;
    private final ProjectRepository projectRepository;
    private final DeckSpecSchemaService deckSpecSchemaService;

    public DeckVersionService(
        DeckVersionRepository deckVersionRepository,
        ProjectRepository projectRepository,
        DeckSpecSchemaService deckSpecSchemaService
    ) {
        this.deckVersionRepository = deckVersionRepository;
        this.projectRepository = projectRepository;
        this.deckSpecSchemaService = deckSpecSchemaService;
    }

    @Transactional(readOnly = true)
    public List<DeckVersionResponse> listVersions(UUID projectId) {
        return deckVersionRepository.findByProjectIdOrderByVersionNumberDesc(projectId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public DeckVersionResponse createVersion(ProjectEntity project, JsonNode deckSpec, DeckVersionSource source, String note) {
        deckSpecSchemaService.validate(deckSpec);

        int nextVersionNumber = deckVersionRepository.findTopByProjectIdOrderByVersionNumberDesc(project.getId())
            .map(entity -> entity.getVersionNumber() + 1)
            .orElse(1);

        DeckVersionEntity entity = new DeckVersionEntity();
        entity.setId(UUID.randomUUID());
        entity.setProjectId(project.getId());
        entity.setVersionNumber(nextVersionNumber);
        entity.setSource(source);
        entity.setNote(note);
        entity.setTemplateId(deckSpec.path("templateId").asText(project.getTemplateId()));
        entity.setSpecTitle(deckSpec.path("title").asText(project.getTitle()));
        entity.setSlideCount(deckSpec.path("slides").size());
        entity.setSpecJson(deckSpec);

        deckVersionRepository.save(entity);
        project.setCurrentVersionId(entity.getId());
        if (deckSpec.hasNonNull("title")) {
            project.setTitle(deckSpec.path("title").asText(project.getTitle()));
        }
        project.setTemplateId(entity.getTemplateId());
        projectRepository.save(project);
        return toResponse(entity);
    }

    @Transactional
    public DeckVersionResponse createVersion(UUID projectId, CreateDeckVersionRequest request) {
        ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
        return createVersion(project, request.deckSpec(), request.source(), request.note());
    }

    @Transactional(readOnly = true)
    public DeckVersionEntity findEntity(UUID versionId) {
        return deckVersionRepository.findById(versionId)
            .orElseThrow(() -> new NotFoundException("版本不存在: " + versionId));
    }

    public DeckVersionResponse toResponse(DeckVersionEntity entity) {
        return new DeckVersionResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getVersionNumber(),
            entity.getSource(),
            entity.getNote(),
            entity.getTemplateId(),
            entity.getSpecTitle(),
            entity.getSlideCount(),
            entity.getSpecJson(),
            entity.getCreatedAt()
        );
    }
}
