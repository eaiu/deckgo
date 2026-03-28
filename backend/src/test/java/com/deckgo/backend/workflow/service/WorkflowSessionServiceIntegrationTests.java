package com.deckgo.backend.workflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deckgo.backend.common.exception.ValidationException;
import com.deckgo.backend.workflow.dto.CreateWorkflowSessionRequest;
import com.deckgo.backend.workflow.dto.WorkflowCommandRequest;
import com.deckgo.backend.workflow.dto.WorkflowSessionResponse;
import com.deckgo.backend.workflow.enums.WorkflowCommandType;
import com.deckgo.backend.workflow.enums.WorkflowSessionStatus;
import com.deckgo.backend.workflow.enums.WorkflowStage;
import com.deckgo.backend.workflow.repository.WorkflowVersionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "deckgo.ai.tavily.enabled=false",
    "deckgo.ai.workflow.discovery.enabled=false",
    "deckgo.ai.workflow.research.enabled=false",
    "deckgo.ai.workflow.outline.enabled=false",
    "deckgo.ai.workflow.page-plan.enabled=false",
    "deckgo.ai.workflow.svg-design.enabled=false"
})
class WorkflowSessionServiceIntegrationTests {

    @Autowired
    private WorkflowSessionService workflowSessionService;

    @Autowired
    private WorkflowVersionRepository workflowVersionRepository;

    @Test
    void shouldRunNewWorkflowOrderEndToEndWithoutCreatingLegacyDeckVersions() {
        WorkflowSessionResponse created = workflowSessionService.createSession(
            new CreateWorkflowSessionRequest("为一个 AI 项目写一份产品介绍 PPT")
        );

        assertEquals(WorkflowStage.DISCOVERY, created.currentStage());
        assertEquals(WorkflowSessionStatus.WAITING_USER, created.status());
        assertNotNull(created.backgroundSummary());
        assertNotNull(created.discoveryCard());
        assertNull(created.discoveryAnswers());
        assertNull(created.outline());

        WorkflowSessionResponse outlined = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(
                WorkflowCommandType.SUBMIT_DISCOVERY,
                List.of("count-10-15", "scene-report"),
                "希望整体更偏正式汇报",
                null
            )
        );

        assertEquals(WorkflowStage.OUTLINE, outlined.currentStage());
        assertNotNull(outlined.discoveryAnswers());
        assertNotNull(outlined.outline());
        assertTrue(outlined.outline().path("sections").size() >= 1);

        WorkflowSessionResponse revised = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.APPLY_OUTLINE_FEEDBACK, null, null, "把第二部分改得更偏方案说明")
        );

        assertEquals(WorkflowStage.OUTLINE, revised.currentStage());
        assertTrue(revised.outline().path("sections").get(0).path("revisionNote").asText("").contains("方案说明"));
        assertNull(revised.pageResearch());

        WorkflowSessionResponse researched = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.CONTINUE_TO_RESEARCH, null, null, null)
        );

        assertEquals(WorkflowStage.RESEARCH, researched.currentStage());
        assertNotNull(researched.pageResearch());
        assertTrue(researched.pageResearch().isArray());

        WorkflowSessionResponse planned = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.CONTINUE_TO_PLANNING, null, null, null)
        );

        assertEquals(WorkflowStage.PLANNING, planned.currentStage());
        assertNotNull(planned.currentVersionId());
        assertFalse(planned.pages().isEmpty());
        assertNotNull(planned.pages().get(0).pagePlan());
        assertTrue(planned.pages().get(0).draftSvg().contains("<svg"));

        WorkflowSessionResponse designed = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.CONTINUE_TO_DESIGN, null, null, null)
        );

        assertEquals(WorkflowStage.DESIGN, designed.currentStage());
        assertEquals(WorkflowSessionStatus.COMPLETED, designed.status());
        assertTrue(designed.pages().stream().allMatch(page -> page.finalSvg() != null && page.finalSvg().contains("<svg")));

        assertThrows(
            ValidationException.class,
            () -> workflowSessionService.executeCommand(
                created.sessionId(),
                new WorkflowCommandRequest(WorkflowCommandType.CONTINUE_TO_DESIGN, null, null, null)
            )
        );
        assertEquals(2, workflowVersionRepository.findTopByProjectIdOrderByVersionNumberDesc(created.project().id()).orElseThrow().getVersionNumber());
    }
}
