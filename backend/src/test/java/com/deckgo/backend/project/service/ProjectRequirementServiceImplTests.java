package com.deckgo.backend.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deckgo.backend.project.dto.RequirementAnswerItemRequest;
import com.deckgo.backend.project.dto.RequirementAnswerPatchRequest;
import com.deckgo.backend.project.dto.RequirementAnswersBatchRequest;
import com.deckgo.backend.project.dto.RequirementConfirmRequest;
import com.deckgo.backend.project.mapper.RequirementFormMapper;
import com.deckgo.backend.project.pojo.RequirementFormPO;
import com.deckgo.backend.project.service.impl.ProjectRequirementServiceImpl;
import com.deckgo.backend.studio.dto.ProjectStudioCommandRequest;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.enums.WorkflowCommandType;
import com.deckgo.backend.studio.service.ProjectStudioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectRequirementServiceImplTests {

    @Mock
    private RequirementFormMapper requirementFormMapper;

    @Mock
    private ProjectStudioService projectStudioService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMergeAnswersByQuestionCode() {
        ProjectRequirementServiceImpl service = new ProjectRequirementServiceImpl(
            requirementFormMapper,
            projectStudioService,
            objectMapper
        );
        UUID projectId = UUID.randomUUID();
        UUID formId = UUID.randomUUID();
        when(requirementFormMapper.selectByProjectId(projectId)).thenReturn(requirementForm(formId, "{\"questionAnswers\":{\"page-count\":[\"count-10-15\"]}}"));

        ObjectNode usageScenario = objectMapper.createObjectNode();
        usageScenario.putArray("selectedOptionIds").add("scene-report");

        service.submitRequirementAnswers(
            projectId,
            new RequirementAnswersBatchRequest(List.of(
                new RequirementAnswerItemRequest("usage-scenario", usageScenario)
            ))
        );

        ArgumentCaptor<String> answersCaptor = ArgumentCaptor.forClass(String.class);
        verify(requirementFormMapper).updateAnswersJson(eq(formId), answersCaptor.capture(), eq("WAITING_USER"), any(OffsetDateTime.class));
        assertEquals(
            "{\"questionAnswers\":{\"page-count\":[\"count-10-15\"],\"usage-scenario\":{\"selectedOptionIds\":[\"scene-report\"]}}}",
            answersCaptor.getValue()
        );
    }

    @Test
    void shouldConvertStoredRequirementAnswersToStudioCommandOnConfirm() {
        ProjectRequirementServiceImpl service = new ProjectRequirementServiceImpl(
            requirementFormMapper,
            projectStudioService,
            objectMapper
        );
        UUID projectId = UUID.randomUUID();
        UUID formId = UUID.randomUUID();
        when(requirementFormMapper.selectByProjectId(projectId)).thenReturn(requirementForm(
            formId,
            "{\"questionAnswers\":{\"page-count\":[\"count-10-15\"],\"usage-scenario\":{\"selectedOptionIds\":[\"scene-report\"]}},\"freeformAnswer\":\"强调业务价值\"}"
        ));
        when(projectStudioService.executeCommand(eq(projectId), any(ProjectStudioCommandRequest.class))).thenReturn(
            new ProjectStudioSnapshot(projectId, "t", "topic", "audience", "clarity-blue", "req", "OUTLINE", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), OffsetDateTime.now(), OffsetDateTime.now())
        );

        ProjectStudioSnapshot snapshot = service.confirmRequirements(projectId, new RequirementConfirmRequest(null));

        ArgumentCaptor<ProjectStudioCommandRequest> commandCaptor = ArgumentCaptor.forClass(ProjectStudioCommandRequest.class);
        verify(projectStudioService).executeCommand(eq(projectId), commandCaptor.capture());
        ProjectStudioCommandRequest command = commandCaptor.getValue();
        assertEquals(WorkflowCommandType.SUBMIT_DISCOVERY, command.command());
        assertEquals(List.of("count-10-15", "scene-report"), command.selectedOptionIds());
        assertEquals("强调业务价值", command.freeformAnswer());
        assertNotNull(snapshot);
    }

    @Test
    void shouldPatchSingleRequirementAnswer() {
        ProjectRequirementServiceImpl service = new ProjectRequirementServiceImpl(
            requirementFormMapper,
            projectStudioService,
            objectMapper
        );
        UUID projectId = UUID.randomUUID();
        UUID formId = UUID.randomUUID();
        when(requirementFormMapper.selectByProjectId(projectId)).thenReturn(requirementForm(formId, null));
        when(requirementFormMapper.selectByProjectId(projectId)).thenReturn(requirementForm(formId, "{\"questionAnswers\":{\"page-count\":[\"count-5-10\"]}}"));

        service.patchRequirementAnswer(
            projectId,
            "page-count",
            new RequirementAnswerPatchRequest(objectMapper.createArrayNode().add("count-5-10"))
        );

        verify(requirementFormMapper).updateAnswersJson(eq(formId), eq("{\"questionAnswers\":{\"page-count\":[\"count-5-10\"]}}"), eq("WAITING_USER"), any(OffsetDateTime.class));
    }

    private RequirementFormPO requirementForm(UUID formId, String answersJson) {
        RequirementFormPO form = new RequirementFormPO();
        form.setId(formId);
        form.setProjectId(UUID.randomUUID());
        form.setStatus("WAITING_USER");
        form.setSummaryMd("summary");
        form.setAnswersJson(answersJson);
        form.setCreatedAt(OffsetDateTime.now());
        form.setUpdatedAt(OffsetDateTime.now());
        return form;
    }
}
