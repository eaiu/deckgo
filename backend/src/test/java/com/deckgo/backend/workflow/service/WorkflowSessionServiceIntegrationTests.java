package com.deckgo.backend.workflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deckgo.backend.deckspec.repository.DeckVersionRepository;
import com.deckgo.backend.workflow.dto.CreateWorkflowSessionRequest;
import com.deckgo.backend.workflow.dto.WorkflowCommandRequest;
import com.deckgo.backend.workflow.dto.WorkflowSessionResponse;
import com.deckgo.backend.workflow.enums.WorkflowCommandType;
import com.deckgo.backend.workflow.enums.WorkflowSessionStatus;
import com.deckgo.backend.workflow.enums.WorkflowStage;
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
    "deckgo.render-worker-delay-ms=600000"
})
class WorkflowSessionServiceIntegrationTests {

    @Autowired
    private WorkflowSessionService workflowSessionService;

    @Autowired
    private DeckVersionRepository deckVersionRepository;

    @Test
    void shouldRunSvgWorkflowStagesEndToEndWithoutCreatingLegacyDeckVersions() {
        WorkflowSessionResponse created = workflowSessionService.createSession(
            new CreateWorkflowSessionRequest("为一个 AI 项目写一份产品介绍 PPT")
        );

        assertEquals(WorkflowStage.DISCOVERY, created.currentStage());
        assertEquals(WorkflowSessionStatus.WAITING_USER, created.status());
        assertEquals(2, created.messages().size());
        assertNotNull(created.discoveryCard());
        assertNull(created.currentVersionId());
        assertEquals(0, deckVersionRepository.count());

        WorkflowSessionResponse researched = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(
                WorkflowCommandType.SUBMIT_DISCOVERY,
                List.of("management", "comparison-yes"),
                "希望最后有一页行动建议",
                null
            )
        );

        assertEquals(WorkflowStage.RESEARCH, researched.currentStage());
        assertNotNull(researched.researchSummary());
        assertEquals("管理层", researched.project().audience());

        WorkflowSessionResponse outlined = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.CONTINUE_TO_OUTLINE, null, null, null)
        );

        assertEquals(WorkflowStage.OUTLINE, outlined.currentStage());
        assertTrue(outlined.outline().path("sections").size() >= 1);

        WorkflowSessionResponse revised = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.APPLY_OUTLINE_FEEDBACK, null, null, "把第二部分改得更偏方案说明")
        );

        assertEquals(WorkflowStage.OUTLINE, revised.currentStage());
        assertTrue(revised.outline().path("sections").get(0).path("revisionNote").asText("").contains("方案说明"));

        WorkflowSessionResponse drafted = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.CONTINUE_TO_PAGE_PLAN, null, null, null)
        );

        assertEquals(WorkflowStage.DRAFT, drafted.currentStage());
        assertNotNull(drafted.currentVersionId());
        assertFalse(drafted.pages().isEmpty());
        assertNotNull(drafted.pages().get(0).pagePlan());
        assertTrue(drafted.pages().get(0).draftSvg().contains("<svg"));

        WorkflowSessionResponse finalized = workflowSessionService.executeCommand(
            created.sessionId(),
            new WorkflowCommandRequest(WorkflowCommandType.CONTINUE_TO_FINAL_DESIGN, null, null, null)
        );

        assertEquals(WorkflowStage.FINAL, finalized.currentStage());
        assertEquals(WorkflowSessionStatus.COMPLETED, finalized.status());
        assertTrue(finalized.pages().stream().allMatch(page -> page.finalSvg() != null && page.finalSvg().contains("<svg")));
        assertEquals(0, deckVersionRepository.count());
    }
}
