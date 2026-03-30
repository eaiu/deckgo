package com.deckgo.backend.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deckgo.backend.project.dto.ProjectCreateRequest;
import com.deckgo.backend.project.dto.ProjectDetailResponse;
import com.deckgo.backend.project.mapper.ProjectMapper;
import com.deckgo.backend.project.pojo.ProjectPO;
import com.deckgo.backend.project.service.impl.ProjectServiceImpl;
import com.deckgo.backend.studio.service.ProjectStudioService;
import com.deckgo.backend.template.dto.TemplateSummary;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTests {

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private TemplateCatalogService templateCatalogService;

    @Mock
    private ProjectStudioService projectStudioService;

    @Test
    void shouldCreateProjectWithMinimalPayload() {
        ProjectServiceImpl projectService = new ProjectServiceImpl(
            projectMapper,
            templateCatalogService,
            projectStudioService,
            new ObjectMapper()
        );
        when(templateCatalogService.getTemplate("clarity-blue")).thenReturn(new TemplateSummary(
            "clarity-blue",
            "Clarity Blue",
            "desc",
            List.of("title"),
            new ObjectMapper().createObjectNode()
        ));
        when(projectMapper.insertProject(any(ProjectPO.class))).thenReturn(1);

        ProjectDetailResponse response = projectService.createProject(new ProjectCreateRequest(
            "季度复盘",
            "围绕 AI 产品增长做季度复盘"
        ));

        ArgumentCaptor<ProjectPO> captor = ArgumentCaptor.forClass(ProjectPO.class);
        verify(projectMapper).insertProject(captor.capture());
        verify(projectStudioService).bootstrapProject(any(), any());
        ProjectPO created = captor.getValue();

        assertNotNull(created.getId());
        assertEquals("季度复盘", created.getTitle());
        assertEquals("围绕 AI 产品增长做季度复盘", created.getTopic());
        assertEquals("待确认", created.getAudience());
        assertEquals("clarity-blue", created.getTemplateId());
        assertNull(created.getStylePreset());
        assertEquals("DISCOVERY", created.getCurrentStage());
        assertEquals("围绕 AI 产品增长做季度复盘", created.getRequestText());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertEquals("clarity-blue", response.templateId());
        assertEquals("DISCOVERY", response.currentStage());
        assertNull(response.workflowConstraints());
    }
}
