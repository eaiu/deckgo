package com.deckgo.backend.project.service;

import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.deckspec.enums.DeckVersionSource;
import com.deckgo.backend.deckspec.service.DeckSpecFactory;
import com.deckgo.backend.deckspec.service.DeckVersionService;
import com.deckgo.backend.project.dto.CreateProjectRequest;
import com.deckgo.backend.project.dto.ProjectResponse;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.repository.ProjectRepository;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TemplateCatalogService templateCatalogService;
    private final DeckSpecFactory deckSpecFactory;
    private final DeckVersionService deckVersionService;

    public ProjectService(
        ProjectRepository projectRepository,
        TemplateCatalogService templateCatalogService,
        DeckSpecFactory deckSpecFactory,
        DeckVersionService deckVersionService
    ) {
        this.projectRepository = projectRepository;
        this.templateCatalogService = templateCatalogService;
        this.deckSpecFactory = deckSpecFactory;
        this.deckVersionService = deckVersionService;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects() {
        return projectRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId) {
        return toResponse(findEntity(projectId));
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        templateCatalogService.getTemplate(request.templateId());

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setTitle(request.title());
        project.setTopic(request.topic());
        project.setAudience(request.audience());
        project.setTemplateId(request.templateId());
        projectRepository.save(project);

        JsonNode initialSpec = deckSpecFactory.createInitialDeckSpec(
            project.getId(),
            request.title(),
            request.topic(),
            request.audience(),
            request.templateId()
        );

        UUID versionId = deckVersionService.createVersion(
            project,
            initialSpec,
            DeckVersionSource.SYSTEM,
            "初始化项目骨架"
        ).id();

        project.setCurrentVersionId(versionId);
        projectRepository.save(project);
        return toResponse(project);
    }

    @Transactional
    public ProjectEntity createWorkflowProject(String title, String topic, String audience, String templateId) {
        templateCatalogService.getTemplate(templateId);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setTitle(title);
        project.setTopic(topic);
        project.setAudience(audience);
        project.setTemplateId(templateId);
        return projectRepository.save(project);
    }

    public ProjectEntity findEntity(UUID projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
    }

    @Transactional
    public ProjectEntity saveEntity(ProjectEntity project) {
        return projectRepository.save(project);
    }

    private ProjectResponse toResponse(ProjectEntity entity) {
        return new ProjectResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getTopic(),
            entity.getAudience(),
            entity.getTemplateId(),
            entity.getCurrentVersionId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
