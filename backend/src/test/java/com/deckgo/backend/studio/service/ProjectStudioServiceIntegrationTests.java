package com.deckgo.backend.studio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deckgo.backend.studio.dto.CreateStudioProjectRequest;
import com.deckgo.backend.studio.dto.ProjectStudioCommandRequest;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.enums.WorkflowCommandType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:studio;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:schema-studio.sql",
    "deckgo.ai.tavily.enabled=false",
    "deckgo.ai.workflow.discovery.enabled=false",
    "deckgo.ai.workflow.research.enabled=false",
    "deckgo.ai.workflow.outline.enabled=false",
    "deckgo.ai.workflow.page-plan.enabled=false",
    "deckgo.ai.workflow.svg-design.enabled=false"
})
class ProjectStudioServiceIntegrationTests {

    @Autowired
    private ProjectStudioService projectStudioService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndRunProjectThroughDesignWithPageHeads() throws Exception {
        ProjectStudioSnapshot created = projectStudioService.createProject(
            new CreateStudioProjectRequest("为一个 AI 项目写一份产品介绍 PPT", 12, null, null, objectMapper.createObjectNode().put("tone", "formal"))
        );

        assertEquals("DISCOVERY", created.currentStage());
        assertNotNull(created.requirementForm());
        assertFalse(created.messages().isEmpty());
        assertNotNull(created.requirementForm().initSearchResults());

        Map<String, Object> discoveryRun = jdbcTemplate.queryForMap(
            "select stage, status, output_ref_json from project_stage_runs where project_id = ? order by started_at desc limit 1",
            created.projectId()
        );
        assertEquals("DISCOVERY", discoveryRun.get("stage"));
        assertEquals("COMPLETED", discoveryRun.get("status"));

        String assistantPayload = jdbcTemplate.queryForObject(
            "select structured_payload_json from project_messages where project_id = ? and role = ? order by created_at desc limit 1",
            String.class,
            created.projectId(),
            "ASSISTANT"
        );
        assertNotNull(assistantPayload);

        ProjectStudioSnapshot outlined = projectStudioService.executeCommand(
            created.projectId(),
            new ProjectStudioCommandRequest(
                WorkflowCommandType.SUBMIT_DISCOVERY,
                "PROJECT",
                null,
                List.of("count-10-15", "scene-report"),
                "整体希望更偏正式汇报",
                null
            )
        );

        assertEquals("OUTLINE", outlined.currentStage());
        assertNotNull(outlined.currentOutline());
        assertFalse(outlined.pages().isEmpty());
        assertTrue(outlined.pages().stream().allMatch(page -> "READY".equals(page.outlineStatus())));

        ProjectStudioSnapshot researched = projectStudioService.executeCommand(
            created.projectId(),
            new ProjectStudioCommandRequest(WorkflowCommandType.CONTINUE_TO_RESEARCH, "PROJECT", null, null, null, null)
        );

        assertEquals("RESEARCH", researched.currentStage());
        Integer researchSessionCount = jdbcTemplate.queryForObject(
            "select count(*) from research_sessions where project_id = ?",
            Integer.class,
            created.projectId()
        );
        assertTrue(researchSessionCount != null && researchSessionCount > 0);
        assertTrue(researched.pages().stream().anyMatch(page -> "COMPLETED".equals(page.searchStatus())));

        ProjectStudioSnapshot planned = projectStudioService.executeCommand(
            created.projectId(),
            new ProjectStudioCommandRequest(WorkflowCommandType.CONTINUE_TO_PLANNING, "PROJECT", null, null, null, null)
        );

        assertEquals("PLANNING", planned.currentStage());
        Integer briefCount = jdbcTemplate.queryForObject("select count(*) from page_brief_versions where project_id = ?", Integer.class, created.projectId());
        Integer draftCount = jdbcTemplate.queryForObject("select count(*) from draft_versions where project_id = ?", Integer.class, created.projectId());
        Integer draftSvgCount = jdbcTemplate.queryForObject("select count(*) from draft_versions where project_id = ? and draft_svg_markup is not null", Integer.class, created.projectId());
        assertTrue(briefCount != null && briefCount > 0);
        assertTrue(draftCount != null && draftCount > 0);
        assertTrue(draftSvgCount != null && draftSvgCount > 0);

        ProjectStudioSnapshot designed = projectStudioService.executeCommand(
            created.projectId(),
            new ProjectStudioCommandRequest(WorkflowCommandType.CONTINUE_TO_DESIGN, "PROJECT", null, null, null, null)
        );

        assertEquals("DESIGN", designed.currentStage());
        Integer designCount = jdbcTemplate.queryForObject("select count(*) from design_versions where project_id = ?", Integer.class, created.projectId());
        Integer finalSvgCount = jdbcTemplate.queryForObject("select count(*) from design_versions where project_id = ? and design_svg_markup is not null", Integer.class, created.projectId());
        assertTrue(designCount != null && designCount > 0);
        assertTrue(finalSvgCount != null && finalSvgCount > 0);
        assertFalse(designed.projectRuns().isEmpty());
    }

    @Test
    void shouldAllowReusingSameChunkAcrossResearchSessions() {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into projects (id, title, topic, audience, template_id, created_at, updated_at, current_stage) values (?, ?, ?, ?, ?, ?, ?, ?)",
            projectId,
            "P",
            "T",
            "A",
            "clarity-blue",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "DISCOVERY"
        );
        UUID pageId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into project_pages (id, project_id, page_code, page_role, part_title, sort_order, outline_status, search_status, summary_status, draft_status, design_status, artifact_staleness_json, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)",
            pageId,
            projectId,
            "page-1",
            "content",
            "part",
            1,
            "READY",
            "PENDING",
            "PENDING",
            "PENDING",
            "PENDING",
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
        UUID collectionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        jdbcTemplate.update("insert into source_collections (id, project_id, page_id, collection_type, title, created_at) values (?, ?, ?, ?, ?, ?)", collectionId, projectId, pageId, "RESEARCH_SESSION", "col", OffsetDateTime.now());
        jdbcTemplate.update("insert into source_documents (id, collection_id, source_type, source_uri, title, status, created_at) values (?, ?, ?, ?, ?, ?, ?)", documentId, collectionId, "WEB", "https://example.com", "doc", "COMPLETED", OffsetDateTime.now());
        jdbcTemplate.update("insert into source_chunks (id, source_document_id, chunk_index, section_path, content_md, content_for_embedding, embedding, token_count, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", chunkId, documentId, 0, "s", "body", "body", null, 10, OffsetDateTime.now());

        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        jdbcTemplate.update("insert into research_sessions (id, project_id, page_id, scope_type, session_role, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)", sessionA, projectId, pageId, "PAGE", "CURRENT", "COMPLETED", OffsetDateTime.now(), OffsetDateTime.now());
        jdbcTemplate.update("insert into research_sessions (id, project_id, page_id, scope_type, session_role, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)", sessionB, projectId, pageId, "PAGE", "CURRENT", "COMPLETED", OffsetDateTime.now(), OffsetDateTime.now());

        jdbcTemplate.update("insert into research_session_sources (id, research_session_id, source_document_id, chunk_id, rank_no, is_pinned) values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), sessionA, documentId, chunkId, 1, true);
        jdbcTemplate.update("insert into research_session_sources (id, research_session_id, source_document_id, chunk_id, rank_no, is_pinned) values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), sessionB, documentId, chunkId, 1, false);

        Integer count = jdbcTemplate.queryForObject("select count(*) from research_session_sources where chunk_id = ?", Integer.class, chunkId);
        assertEquals(2, count);
    }

}
