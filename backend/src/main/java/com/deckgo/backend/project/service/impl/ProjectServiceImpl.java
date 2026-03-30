package com.deckgo.backend.project.service.impl;

import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.project.dto.ProjectCreateRequest;
import com.deckgo.backend.project.dto.ProjectDetailResponse;
import com.deckgo.backend.project.dto.ProjectResponse;
import com.deckgo.backend.project.dto.ProjectUpdateRequest;
import com.deckgo.backend.project.mapper.ProjectMapper;
import com.deckgo.backend.project.pojo.ProjectPO;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.studio.dto.CreateStudioProjectRequest;
import com.deckgo.backend.studio.enums.WorkflowStage;
import com.deckgo.backend.studio.service.ProjectStudioService;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final String DEFAULT_TEMPLATE_ID = "clarity-blue";

    private final ProjectMapper projectMapper;
    private final TemplateCatalogService templateCatalogService;
    private final ProjectStudioService projectStudioService;
    private final ObjectMapper objectMapper;

    public ProjectServiceImpl(
        ProjectMapper projectMapper,
        TemplateCatalogService templateCatalogService,
        ProjectStudioService projectStudioService,
        ObjectMapper objectMapper
    ) {
        this.projectMapper = projectMapper;
        this.templateCatalogService = templateCatalogService;
        this.projectStudioService = projectStudioService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects() {
        return projectMapper.selectProjects().stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(UUID projectId) {
        return toDetail(findProject(projectId));
    }

    @Override
    @Transactional
    public ProjectDetailResponse createProject(ProjectCreateRequest request) {
        String templateId = DEFAULT_TEMPLATE_ID;
        templateCatalogService.getTemplate(templateId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ProjectPO project = new ProjectPO();
        project.setId(UUID.randomUUID());
        project.setTitle(request.title().trim());
        project.setTopic(request.requestText().trim());
        project.setAudience("待确认");
        project.setTemplateId(templateId);
        project.setRequestText(request.requestText().trim());
        project.setCurrentStage(WorkflowStage.DISCOVERY.name());
        project.setCurrentOutlineVersionId(null);
        project.setPageCountTarget(null);
        project.setStylePreset(null);
        project.setBackgroundAssetPath(null);
        project.setWorkflowConstraintsJson(null);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        projectMapper.insertProject(project);
        projectStudioService.bootstrapProject(
            project.getId(),
            new CreateStudioProjectRequest(
                request.requestText().trim(),
                null,
                null,
                null,
                null
            )
        );
        return toDetail(project);
    }

    @Override
    @Transactional
    public ProjectDetailResponse updateProject(UUID projectId, ProjectUpdateRequest request) {
        ProjectPO existing = findProject(projectId);
        String templateId = resolveTemplateId(request);
        templateCatalogService.getTemplate(templateId);

        existing.setTitle(request.title().trim());
        existing.setTopic(request.topic().trim());
        existing.setAudience(request.audience().trim());
        existing.setTemplateId(templateId);
        existing.setRequestText(hasText(request.requestText()) ? request.requestText().trim() : request.topic().trim());
        existing.setPageCountTarget(request.pageCountTarget());
        existing.setStylePreset(hasText(request.stylePreset()) ? request.stylePreset().trim() : templateId);
        existing.setBackgroundAssetPath(trimToNull(request.backgroundAssetPath()));
        existing.setWorkflowConstraintsJson(writeJson(request.workflowConstraints()));
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        projectMapper.updateProject(existing);
        return toDetail(existing);
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId) {
        findProject(projectId);
        projectMapper.deleteProject(projectId);
    }

    private ProjectPO findProject(UUID projectId) {
        ProjectPO project = projectMapper.selectProjectById(projectId);
        if (project == null) {
            throw new NotFoundException("项目不存在: " + projectId);
        }
        return project;
    }

    private ProjectResponse toSummary(ProjectPO project) {
        return new ProjectResponse(
            project.getId(),
            project.getTitle(),
            project.getTopic(),
            project.getRequestText(),
            project.getCurrentStage(),
            project.getPageCountTarget(),
            project.getStylePreset(),
            project.getBackgroundAssetPath(),
            project.getAudience(),
            project.getTemplateId(),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }

    private ProjectDetailResponse toDetail(ProjectPO project) {
        return new ProjectDetailResponse(
            project.getId(),
            project.getTitle(),
            project.getTopic(),
            project.getAudience(),
            project.getTemplateId(),
            project.getRequestText(),
            project.getCurrentStage(),
            project.getCurrentOutlineVersionId(),
            project.getPageCountTarget(),
            project.getStylePreset(),
            project.getBackgroundAssetPath(),
            readJson(project.getWorkflowConstraintsJson()),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }

    private String resolveTemplateId(ProjectUpdateRequest request) {
        if (hasText(request.templateId())) {
            return request.templateId().trim();
        }
        if (hasText(request.stylePreset())) {
            return request.stylePreset().trim();
        }
        return DEFAULT_TEMPLATE_ID;
    }

    private JsonNode readJson(String jsonText) {
        if (!hasText(jsonText)) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonText);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("解析项目 JSON 字段失败", exception);
        }
    }

    private String writeJson(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化项目 JSON 字段失败", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
