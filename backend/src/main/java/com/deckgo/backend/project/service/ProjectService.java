package com.deckgo.backend.project.service;

import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.project.dto.ProjectResponse;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.repository.ProjectRepository;
import com.deckgo.backend.template.service.TemplateCatalogService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TemplateCatalogService templateCatalogService;

    public ProjectService(
        ProjectRepository projectRepository,
        TemplateCatalogService templateCatalogService
    ) {
        this.projectRepository = projectRepository;
        this.templateCatalogService = templateCatalogService;
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
    public ProjectEntity createWorkflowProject(String title, String topic, String audience, String templateId) {
        templateCatalogService.getTemplate(templateId);
        return projectRepository.save(newProject(title, topic, audience, templateId));
    }

    public ProjectEntity findEntity(UUID projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
    }

    @Transactional
    public ProjectEntity findEntityForUpdate(UUID projectId) {
        ProjectEntity project = projectRepository.findByIdForUpdate(projectId);
        if (project == null) {
            throw new NotFoundException("项目不存在: " + projectId);
        }
        return project;
    }

    @Transactional
    public ProjectEntity saveEntity(ProjectEntity project) {
        return projectRepository.save(project);
    }

    private ProjectEntity newProject(String title, String topic, String audience, String templateId) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setTitle(title);
        project.setTopic(topic);
        project.setAudience(audience);
        project.setTemplateId(templateId);
        return project;
    }

    private ProjectResponse toResponse(ProjectEntity entity) {
        return new ProjectResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getTopic(),
            entity.getAudience(),
            entity.getTemplateId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
