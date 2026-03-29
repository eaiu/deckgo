package com.deckgo.backend.studio.service;

import com.deckgo.backend.ai.service.DiscoveryAgentService;
import com.deckgo.backend.ai.service.OutlineAgentService;
import com.deckgo.backend.ai.service.PagePlanAgentService;
import com.deckgo.backend.ai.service.ResearchAgentService;
import com.deckgo.backend.ai.service.SvgDesignAgentService;
import com.deckgo.backend.ai.service.TavilySearchService;
import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.common.exception.ValidationException;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.repository.ProjectRepository;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.studio.dto.CreateStudioProjectRequest;
import com.deckgo.backend.studio.dto.OutlineVersionSnapshot;
import com.deckgo.backend.studio.dto.ProjectMessageSnapshot;
import com.deckgo.backend.studio.dto.ProjectPageSnapshot;
import com.deckgo.backend.studio.dto.ProjectStudioChatRequest;
import com.deckgo.backend.studio.dto.ProjectStudioChatResponse;
import com.deckgo.backend.studio.dto.ProjectStudioCommandRequest;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.dto.RequirementFormSnapshot;
import com.deckgo.backend.studio.dto.StageRunSnapshot;
import com.deckgo.backend.workflow.enums.WorkflowCommandType;
import com.deckgo.backend.workflow.enums.WorkflowMessageRole;
import com.deckgo.backend.workflow.enums.WorkflowStage;
import com.deckgo.backend.workflow.service.WorkflowContentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectStudioService {

    private static final String SCOPE_PROJECT = "PROJECT";
    private static final String SCOPE_PAGE = "PAGE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_WAITING_USER = "WAITING_USER";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_STALE = "STALE";
    private static final String STATUS_READY = "READY";

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final WorkflowContentService workflowContentService;
    private final TavilySearchService tavilySearchService;
    private final DiscoveryAgentService discoveryAgentService;
    private final OutlineAgentService outlineAgentService;
    private final ResearchAgentService researchAgentService;
    private final PagePlanAgentService pagePlanAgentService;
    private final SvgDesignAgentService svgDesignAgentService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProjectStudioService(
        ProjectService projectService,
        ProjectRepository projectRepository,
        WorkflowContentService workflowContentService,
        TavilySearchService tavilySearchService,
        DiscoveryAgentService discoveryAgentService,
        OutlineAgentService outlineAgentService,
        ResearchAgentService researchAgentService,
        PagePlanAgentService pagePlanAgentService,
        SvgDesignAgentService svgDesignAgentService,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.workflowContentService = workflowContentService;
        this.tavilySearchService = tavilySearchService;
        this.discoveryAgentService = discoveryAgentService;
        this.outlineAgentService = outlineAgentService;
        this.researchAgentService = researchAgentService;
        this.pagePlanAgentService = pagePlanAgentService;
        this.svgDesignAgentService = svgDesignAgentService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectStudioSnapshot createProject(CreateStudioProjectRequest request) {
        String prompt = request.prompt().trim();
        String templateId = request.stylePreset() == null || request.stylePreset().isBlank()
            ? workflowContentService.deriveTemplateId(prompt)
            : request.stylePreset().trim();
        ProjectEntity project = projectService.createWorkflowProject(
            workflowContentService.deriveProjectTitle(prompt),
            prompt,
            "待确认",
            templateId
        );
        projectRepository.flush();

        updateProjectStudioColumns(
            project.getId(),
            prompt,
            WorkflowStage.DISCOVERY.name(),
            null,
            request.pageCountTarget(),
            templateId,
            request.backgroundAssetPath(),
            request.workflowConstraints()
        );

        UUID runId = startProjectStageRun(
            project.getId(),
            WorkflowStage.DISCOVERY.name(),
            initialDiscoveryInput(prompt, templateId, request)
        );
        appendProjectEvent(
            project.getId(),
            "PROJECT_CREATED",
            WorkflowStage.DISCOVERY.name(),
            SCOPE_PROJECT,
            null,
            null,
            projectCreatedPayload(project.getId(), templateId)
        );
        appendProjectEvent(
            project.getId(),
            "BACKGROUND_RESEARCH_STARTED",
            WorkflowStage.DISCOVERY.name(),
            SCOPE_PROJECT,
            null,
            runId,
            objectNode("prompt", prompt)
        );

        JsonNode backgroundSummary = tavilySearchService.collectBackgroundSummary(prompt)
            .orElseGet(() -> workflowContentService.generateBackgroundSummary(project));
        JsonNode discoveryCard = discoveryAgentService.generateDiscoveryCard(project, backgroundSummary);

        UUID requirementFormId = UUID.randomUUID();
        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into requirement_forms (
                id, project_id, status, based_on_outline_version_id, summary_md, outline_context_md,
                fixed_items_json, init_search_queries_json, init_search_results_json, init_corpus_digest_json,
                ai_questions_json, answers_json, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?)
            """,
            requirementFormId,
            project.getId(),
            STATUS_WAITING_USER,
            null,
            backgroundSummary.path("summary").asText(""),
            null,
            jsonText(fixedItemsForCreate(request)),
            jsonText(backgroundSummary.path("sources")),
            jsonText(backgroundSummary),
            jsonText(backgroundDigest(backgroundSummary)),
            jsonText(discoveryCard),
            jsonText(null),
            toTimestamp(now),
            toTimestamp(now)
        );

        finishProjectStageRun(runId, STATUS_COMPLETED, initialDiscoveryOutput(requirementFormId, backgroundSummary, discoveryCard), null);
        appendProjectMessage(project.getId(), WorkflowStage.DISCOVERY.name(), SCOPE_PROJECT, null, WorkflowMessageRole.USER.name(), prompt, textPayload(prompt));
        appendProjectMessage(
            project.getId(),
            WorkflowStage.DISCOVERY.name(),
            SCOPE_PROJECT,
            null,
            WorkflowMessageRole.ASSISTANT.name(),
            "背景调研已完成，并基于它生成了几个需要你确认的问题。",
            initialDiscoveryPayload(backgroundSummary, discoveryCard)
        );
        appendProjectEvent(
            project.getId(),
            "BACKGROUND_RESEARCH_COMPLETED",
            WorkflowStage.DISCOVERY.name(),
            SCOPE_PROJECT,
            null,
            runId,
            backgroundCompletedPayload(requirementFormId, backgroundSummary)
        );
        appendProjectEvent(project.getId(), "DISCOVERY_CARD_GENERATED", WorkflowStage.DISCOVERY.name(), SCOPE_PROJECT, null, runId, discoveryCard);
        return getProject(project.getId());
    }

    @Transactional(readOnly = true)
    public ProjectStudioSnapshot getProject(UUID projectId) {
        Map<String, Object> projectRow = jdbcTemplate.queryForMap(
            """
            select id, title, topic, audience, template_id, request_text, current_stage, current_outline_version_id,
                   page_count_target, style_preset, background_asset_path, workflow_constraints_json, created_at, updated_at
            from projects
            where id = ?
            """,
            projectId
        );
        RequirementFormSnapshot requirementForm = loadRequirementForm(projectId);
        UUID currentOutlineId = uuid(projectRow.get("current_outline_version_id"));
        OutlineVersionSnapshot currentOutline = currentOutlineId == null ? null : loadOutlineVersion(currentOutlineId);
        return new ProjectStudioSnapshot(
            uuid(projectRow.get("id")),
            text(projectRow.get("title")),
            text(projectRow.get("topic")),
            text(projectRow.get("audience")),
            text(projectRow.get("template_id")),
            text(projectRow.get("request_text")),
            text(projectRow.get("current_stage")),
            currentOutlineId,
            integer(projectRow.get("page_count_target")),
            text(projectRow.get("style_preset")),
            text(projectRow.get("background_asset_path")),
            json(projectRow.get("workflow_constraints_json")),
            requirementForm,
            currentOutline,
            loadPages(projectId),
            loadMessages(projectId),
            loadProjectRuns(projectId),
            offset(projectRow.get("created_at")),
            offset(projectRow.get("updated_at"))
        );
    }

    @Transactional(readOnly = true)
    public ProjectPageSnapshot getPage(UUID projectId, UUID pageId) {
        return loadPages(projectId).stream()
            .filter(page -> page.id().equals(pageId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("页面不存在: " + pageId));
    }

    @Transactional
    public ProjectStudioSnapshot executeCommand(UUID projectId, ProjectStudioCommandRequest request) {
        ProjectEntity project = lockProject(projectId);
        try {
            switch (request.command()) {
                case SUBMIT_DISCOVERY -> handleSubmitDiscovery(project, request);
                case APPLY_OUTLINE_FEEDBACK -> handleApplyOutlineFeedback(project, request);
                case CONTINUE_TO_RESEARCH -> handleContinueToResearch(project);
                case CONTINUE_TO_PLANNING -> handleContinueToPlanning(project);
                case CONTINUE_TO_DESIGN -> handleContinueToDesign(project);
                default -> throw new ValidationException("不支持的 command", List.of(request.command().name()));
            }
            return getProject(projectId);
        } catch (RuntimeException exception) {
            appendProjectEvent(projectId, "COMMAND_FAILED", currentProjectStage(projectId), scopeOf(request.targetPageId()), request.targetPageId(), null, errorPayload(exception));
            throw exception;
        }
    }

    @Transactional
    public ProjectStudioChatResponse chat(UUID projectId, ProjectStudioChatRequest request) {
        String message = request.message().trim();
        appendProjectMessage(projectId, currentProjectStage(projectId), SCOPE_PROJECT, null, WorkflowMessageRole.USER.name(), message, textPayload(message));

        String currentStage = currentProjectStage(projectId);
        String normalized = message.toLowerCase();
        ProjectStudioSnapshot snapshot;
        String assistantMessage;
        if (looksLikeContinue(normalized)) {
            WorkflowCommandType command = switch (WorkflowStage.valueOf(currentStage)) {
                case DISCOVERY -> WorkflowCommandType.SUBMIT_DISCOVERY;
                case OUTLINE -> WorkflowCommandType.CONTINUE_TO_RESEARCH;
                case RESEARCH -> WorkflowCommandType.CONTINUE_TO_PLANNING;
                case PLANNING -> WorkflowCommandType.CONTINUE_TO_DESIGN;
                case DESIGN -> null;
            };
            if (command == null) {
                assistantMessage = "当前已经处于最终设计阶段，可按页查看或重设计。";
                snapshot = getProject(projectId);
            } else {
                snapshot = executeCommand(projectId, new ProjectStudioCommandRequest(command, SCOPE_PROJECT, null, List.of(), message, message));
                assistantMessage = "已按当前阶段继续执行。";
            }
        } else if (WorkflowStage.OUTLINE.name().equals(currentStage)) {
            snapshot = executeCommand(projectId, new ProjectStudioCommandRequest(WorkflowCommandType.APPLY_OUTLINE_FEEDBACK, SCOPE_PROJECT, null, null, null, message));
            assistantMessage = "已根据你的反馈更新 outline。";
        } else if (WorkflowStage.DISCOVERY.name().equals(currentStage)) {
            snapshot = executeCommand(projectId, new ProjectStudioCommandRequest(WorkflowCommandType.SUBMIT_DISCOVERY, SCOPE_PROJECT, null, List.of(), message, null));
            assistantMessage = "已根据补充信息生成 outline。";
        } else {
            assistantMessage = "当前阶段更适合通过命令推进；如果你想继续，可以直接发送“继续”。";
            snapshot = getProject(projectId);
        }
        appendProjectMessage(projectId, currentProjectStage(projectId), SCOPE_PROJECT, null, WorkflowMessageRole.ASSISTANT.name(), assistantMessage, textPayload(assistantMessage));
        return new ProjectStudioChatResponse(projectId, assistantMessage, snapshot);
    }

    @Transactional
    public ProjectPageSnapshot redesignPage(UUID projectId, UUID pageId, String instruction) {
        ProjectEntity project = lockProject(projectId);
        Map<String, Object> pageRow = jdbcTemplate.queryForMap("select * from project_pages where id = ? and project_id = ?", pageId, projectId);
        UUID draftVersionId = uuid(pageRow.get("current_draft_version_id"));
        if (draftVersionId == null) {
            throw new ValidationException("当前页面还没有可重设计的 draft", List.of(pageId.toString()));
        }

        JsonNode pagePlan = loadBriefPayload(uuid(pageRow.get("current_brief_version_id")));
        JsonNode research = loadResearchPayload(uuid(pageRow.get("current_research_session_id")));
        UUID runId = startPageStageRun(projectId, pageId, WorkflowStage.DESIGN.name(), objectNode("instruction", instruction == null ? "" : instruction));
        String finalSvg = svgDesignAgentService.generateFinalSvg(pagePlan, research, project.getTemplateId());
        UUID designVersionId = insertDesignVersion(projectId, pageId, draftVersionId, project.getTemplateId(), currentBackgroundAsset(projectId), finalSvg);
        updatePageHeadsAndStatuses(pageId, null, null, null, designVersionId, null, null, null, null, STATUS_COMPLETED, clearStaleness());
        finishPageStageRun(runId, STATUS_COMPLETED, objectNode("designVersionId", designVersionId.toString()), null);
        appendProjectEvent(projectId, "PAGE_REDESIGNED", WorkflowStage.DESIGN.name(), SCOPE_PAGE, pageId, runId, objectNode("designVersionId", designVersionId.toString()));
        return getPage(projectId, pageId);
    }

    private void handleSubmitDiscovery(ProjectEntity project, ProjectStudioCommandRequest request) {
        ensureStage(project.getId(), WorkflowStage.DISCOVERY.name(), request.command());
        RequirementFormSnapshot form = loadRequirementForm(project.getId());
        JsonNode answers = buildDiscoveryAnswers(request);
        appendProjectMessage(project.getId(), WorkflowStage.DISCOVERY.name(), SCOPE_PROJECT, null, WorkflowMessageRole.USER.name(), messageForAnswers(answers), answers);
        updateRequirementFormAnswers(form.id(), answers);

        UUID runId = startProjectStageRun(project.getId(), WorkflowStage.OUTLINE.name(), answers);
        JsonNode background = json(form.initSearchResults());
        JsonNode outline = outlineAgentService.generateOutline(project, background, answers);
        UUID outlineVersionId = insertOutlineVersion(project.getId(), outline, STATUS_COMPLETED, null);
        updateProjectStudioColumns(
            project.getId(),
            currentRequestText(project.getId()),
            WorkflowStage.OUTLINE.name(),
            outlineVersionId,
            derivePageCountTarget(answers),
            currentStylePreset(project.getId(), project.getTemplateId()),
            currentBackgroundAsset(project.getId()),
            currentWorkflowConstraints(project.getId())
        );
        updateRequirementFormForOutline(form.id(), outlineVersionId, outline);
        syncProjectPages(project.getId(), outline, true);
        finishProjectStageRun(runId, STATUS_COMPLETED, objectNode("outlineVersionId", outlineVersionId.toString()), null);
        appendProjectEvent(project.getId(), "OUTLINE_GENERATED", WorkflowStage.OUTLINE.name(), SCOPE_PROJECT, null, runId, objectNode("outlineVersionId", outlineVersionId.toString()));
        appendProjectMessage(project.getId(), WorkflowStage.OUTLINE.name(), SCOPE_PROJECT, null, WorkflowMessageRole.ASSISTANT.name(), "大纲已经生成好了。你可以先检查结构，再继续进入按页资料搜集阶段。", outline);
    }

    private void handleApplyOutlineFeedback(ProjectEntity project, ProjectStudioCommandRequest request) {
        ensureStage(project.getId(), WorkflowStage.OUTLINE.name(), request.command());
        if (request.feedback() == null || request.feedback().isBlank()) {
            throw new ValidationException("outline feedback 不能为空", List.of("feedback"));
        }
        RequirementFormSnapshot form = loadRequirementForm(project.getId());
        OutlineVersionSnapshot currentOutline = loadCurrentOutline(project.getId());
        appendProjectMessage(project.getId(), WorkflowStage.OUTLINE.name(), SCOPE_PROJECT, null, WorkflowMessageRole.USER.name(), request.feedback().trim(), textPayload(request.feedback().trim()));

        UUID runId = startProjectStageRun(project.getId(), WorkflowStage.OUTLINE.name(), textPayload(request.feedback().trim()));
        JsonNode outline = outlineAgentService.reviseOutline(project, json(form.initSearchResults()), nullSafe(form.answers()), currentOutline.outline(), request.feedback().trim());
        UUID outlineVersionId = insertOutlineVersion(project.getId(), outline, STATUS_COMPLETED, currentOutline.id());
        updateProjectStudioColumns(
            project.getId(),
            currentRequestText(project.getId()),
            WorkflowStage.OUTLINE.name(),
            outlineVersionId,
            currentPageCountTarget(project.getId()),
            currentStylePreset(project.getId(), project.getTemplateId()),
            currentBackgroundAsset(project.getId()),
            currentWorkflowConstraints(project.getId())
        );
        updateRequirementFormForOutline(form.id(), outlineVersionId, outline);
        syncProjectPages(project.getId(), outline, true);
        finishProjectStageRun(runId, STATUS_COMPLETED, objectNode("outlineVersionId", outlineVersionId.toString()), null);
        appendProjectEvent(project.getId(), "OUTLINE_REVISED", WorkflowStage.OUTLINE.name(), SCOPE_PROJECT, null, runId, objectNode("outlineVersionId", outlineVersionId.toString()));
        appendProjectMessage(project.getId(), WorkflowStage.OUTLINE.name(), SCOPE_PROJECT, null, WorkflowMessageRole.ASSISTANT.name(), "我已经根据你的要求更新了大纲。确认后就可以继续进行逐页资料搜集。", outline);
    }

    private void handleContinueToResearch(ProjectEntity project) {
        ensureStage(project.getId(), WorkflowStage.OUTLINE.name(), WorkflowCommandType.CONTINUE_TO_RESEARCH);
        RequirementFormSnapshot form = loadRequirementForm(project.getId());
        OutlineVersionSnapshot outlineVersion = loadCurrentOutline(project.getId());
        UUID runId = startProjectStageRun(project.getId(), WorkflowStage.RESEARCH.name(), objectNode("outlineVersionId", outlineVersion.id().toString()));
        JsonNode pageResearch = researchAgentService.generatePageResearch(project, json(form.initSearchResults()), nullSafe(form.answers()), outlineVersion.outline());

        Map<String, UUID> pageIdByCode = loadPageIdsByCode(project.getId());
        List<UUID> updatedPages = new ArrayList<>();
        for (JsonNode item : iterable(pageResearch)) {
            UUID pageId = pageIdByCode.get(item.path("pageId").asText());
            if (pageId == null) {
                continue;
            }
            UUID pageRunId = startPageStageRun(project.getId(), pageId, WorkflowStage.RESEARCH.name(), item);
            UUID sessionId = insertResearchSession(project.getId(), pageId, item);
            persistResearchArtifacts(project.getId(), pageId, sessionId, item);
            updatePageHeadsAndStatuses(pageId, null, sessionId, null, null, null, STATUS_COMPLETED, STATUS_COMPLETED, STATUS_STALE, STATUS_STALE, staleResearchResolved());
            finishPageStageRun(pageRunId, STATUS_COMPLETED, objectNode("researchSessionId", sessionId.toString()), null);
            appendProjectEvent(project.getId(), "PAGE_RESEARCH_COMPLETED", WorkflowStage.RESEARCH.name(), SCOPE_PAGE, pageId, pageRunId, objectNode("researchSessionId", sessionId.toString()));
            updatedPages.add(pageId);
        }

        for (ProjectPageSnapshot page : loadPages(project.getId())) {
            if (updatedPages.contains(page.id())) {
                continue;
            }
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("pageId", page.pageCode());
            fallback.put("title", page.pageCode());
            fallback.put("needsSearch", false);
            fallback.put("searchIntent", "fallback");
            fallback.put("findings", "当前页面暂无外部 research，后续可继续补充。");
            fallback.set("queries", emptyArray());
            fallback.set("sources", emptyArray());

            UUID pageRunId = startPageStageRun(project.getId(), page.id(), WorkflowStage.RESEARCH.name(), fallback);
            UUID sessionId = insertResearchSession(project.getId(), page.id(), fallback);
            updatePageHeadsAndStatuses(page.id(), null, sessionId, null, null, null, STATUS_COMPLETED, STATUS_COMPLETED, STATUS_STALE, STATUS_STALE, staleResearchResolved());
            finishPageStageRun(pageRunId, STATUS_COMPLETED, objectNode("researchSessionId", sessionId.toString()), null);
            appendProjectEvent(project.getId(), "PAGE_RESEARCH_COMPLETED", WorkflowStage.RESEARCH.name(), SCOPE_PAGE, page.id(), pageRunId, objectNode("researchSessionId", sessionId.toString()));
        }

        updateProjectCurrentStage(project.getId(), WorkflowStage.RESEARCH.name());
        finishProjectStageRun(runId, STATUS_COMPLETED, objectNode("stage", WorkflowStage.RESEARCH.name()), null);
        appendProjectEvent(project.getId(), "RESEARCH_COMPLETED", WorkflowStage.RESEARCH.name(), SCOPE_PROJECT, null, runId, objectNode("stage", WorkflowStage.RESEARCH.name()));
        appendProjectMessage(project.getId(), WorkflowStage.RESEARCH.name(), SCOPE_PROJECT, null, WorkflowMessageRole.ASSISTANT.name(), "逐页资料搜集已经完成。现在每一页都绑定了研究结果，可以继续进入策划阶段。", pageResearch);
    }

    private void handleContinueToPlanning(ProjectEntity project) {
        ensureStage(project.getId(), WorkflowStage.RESEARCH.name(), WorkflowCommandType.CONTINUE_TO_PLANNING);
        RequirementFormSnapshot form = loadRequirementForm(project.getId());
        OutlineVersionSnapshot outlineVersion = loadCurrentOutline(project.getId());
        String outlineTitle = outlineVersion.outline().path("title").asText(project.getTitle());
        UUID runId = startProjectStageRun(project.getId(), WorkflowStage.PLANNING.name(), objectNode("outlineVersionId", outlineVersion.id().toString()));

        for (ProjectPageSnapshot page : loadPages(project.getId())) {
            UUID pageRunId = startPageStageRun(project.getId(), page.id(), WorkflowStage.PLANNING.name(), objectNode("pageId", page.id().toString()));
            JsonNode outlinePage = findOutlinePage(outlineVersion.outline(), page.pageCode());
            JsonNode researchPayload = loadResearchPayload(page.currentResearchSessionId());
            JsonNode pagePlan = normalizePagePlan(
                pagePlanAgentService.generateSinglePagePlan(project, json(form.initSearchResults()), outlinePage, researchPayload, outlineTitle, project.getTemplateId()),
                outlinePage,
                researchPayload
            );
            UUID briefVersionId = insertPageBriefVersion(project.getId(), page.id(), page.partTitle(), pagePlan, researchPayload);
            String draftSvg = svgDesignAgentService.generateDraftSvg(pagePlan, researchPayload, project.getTemplateId());
            UUID draftVersionId = insertDraftVersion(project.getId(), page.id(), briefVersionId, page.currentResearchSessionId(), draftSvg);
            updatePageHeadsAndStatuses(page.id(), briefVersionId, null, draftVersionId, null, STATUS_COMPLETED, null, null, STATUS_COMPLETED, STATUS_STALE, planningStaleness());
            finishPageStageRun(pageRunId, STATUS_COMPLETED, objectNode("draftVersionId", draftVersionId.toString()), null);
            appendProjectEvent(project.getId(), "PAGE_PLANNED", WorkflowStage.PLANNING.name(), SCOPE_PAGE, page.id(), pageRunId, objectNode("draftVersionId", draftVersionId.toString()));
        }

        updateProjectCurrentStage(project.getId(), WorkflowStage.PLANNING.name());
        finishProjectStageRun(runId, STATUS_COMPLETED, objectNode("stage", WorkflowStage.PLANNING.name()), null);
        appendProjectEvent(project.getId(), "PLANNING_COMPLETED", WorkflowStage.PLANNING.name(), SCOPE_PROJECT, null, runId, objectNode("stage", WorkflowStage.PLANNING.name()));
        appendProjectMessage(project.getId(), WorkflowStage.PLANNING.name(), SCOPE_PROJECT, null, WorkflowMessageRole.ASSISTANT.name(), "页面 brief 与 draft 已生成完成。", textPayload("planning-completed"));
    }

    private void handleContinueToDesign(ProjectEntity project) {
        ensureStage(project.getId(), WorkflowStage.PLANNING.name(), WorkflowCommandType.CONTINUE_TO_DESIGN);
        OutlineVersionSnapshot outlineVersion = loadCurrentOutline(project.getId());
        UUID runId = startProjectStageRun(project.getId(), WorkflowStage.DESIGN.name(), objectNode("projectId", project.getId().toString()));
        for (ProjectPageSnapshot page : loadPages(project.getId())) {
            if (page.currentDraftVersionId() == null) {
                continue;
            }
            UUID pageRunId = startPageStageRun(project.getId(), page.id(), WorkflowStage.DESIGN.name(), objectNode("draftVersionId", page.currentDraftVersionId().toString()));
            JsonNode pagePlan = normalizePagePlan(
                loadBriefPayload(page.currentBriefVersionId()),
                findOutlinePage(outlineVersion.outline(), page.pageCode()),
                loadResearchPayload(page.currentResearchSessionId())
            );
            JsonNode researchPayload = loadResearchPayload(page.currentResearchSessionId());
            String finalSvg = svgDesignAgentService.generateFinalSvg(pagePlan, researchPayload, project.getTemplateId());
            UUID designVersionId = insertDesignVersion(project.getId(), page.id(), page.currentDraftVersionId(), project.getTemplateId(), currentBackgroundAsset(project.getId()), finalSvg);
            updatePageHeadsAndStatuses(page.id(), null, null, null, designVersionId, null, null, null, null, STATUS_COMPLETED, clearStaleness());
            finishPageStageRun(pageRunId, STATUS_COMPLETED, objectNode("designVersionId", designVersionId.toString()), null);
            appendProjectEvent(project.getId(), "PAGE_DESIGNED", WorkflowStage.DESIGN.name(), SCOPE_PAGE, page.id(), pageRunId, objectNode("designVersionId", designVersionId.toString()));
        }
        updateProjectCurrentStage(project.getId(), WorkflowStage.DESIGN.name());
        finishProjectStageRun(runId, STATUS_COMPLETED, objectNode("stage", WorkflowStage.DESIGN.name()), null);
        appendProjectEvent(project.getId(), "DESIGN_COMPLETED", WorkflowStage.DESIGN.name(), SCOPE_PROJECT, null, runId, objectNode("stage", WorkflowStage.DESIGN.name()));
        appendProjectMessage(project.getId(), WorkflowStage.DESIGN.name(), SCOPE_PROJECT, null, WorkflowMessageRole.ASSISTANT.name(), "最终 SVG 页面集已经生成完成，现在可以逐页查看最终设计稿。", textPayload("design-completed"));
    }

    private ProjectEntity lockProject(UUID projectId) {
        ProjectEntity project = projectRepository.findByIdForUpdate(projectId);
        if (project == null) {
            throw new NotFoundException("项目不存在: " + projectId);
        }
        return project;
    }

    private void ensureStage(UUID projectId, String expectedStage, WorkflowCommandType command) {
        String actualStage = currentProjectStage(projectId);
        if (!Objects.equals(expectedStage, actualStage)) {
            throw new ValidationException("当前阶段不允许执行该命令", List.of("command=%s expectedStage=%s actualStage=%s".formatted(command, expectedStage, actualStage)));
        }
    }

    private String currentProjectStage(UUID projectId) {
        return jdbcTemplate.queryForObject("select current_stage from projects where id = ?", String.class, projectId);
    }

    private void updateProjectCurrentStage(UUID projectId, String stage) {
        jdbcTemplate.update("update projects set current_stage = ?, updated_at = ? where id = ?", stage, toTimestamp(utcNow()), projectId);
    }

    private void updateProjectStudioColumns(
        UUID projectId,
        String requestText,
        String currentStage,
        UUID currentOutlineVersionId,
        Integer pageCountTarget,
        String stylePreset,
        String backgroundAssetPath,
        JsonNode workflowConstraints
    ) {
        jdbcTemplate.update(
            """
            update projects
            set request_text = ?, current_stage = ?, current_outline_version_id = ?, page_count_target = ?, style_preset = ?,
                background_asset_path = ?, workflow_constraints_json = cast(? as jsonb), updated_at = ?
            where id = ?
            """,
            requestText,
            currentStage,
            currentOutlineVersionId,
            pageCountTarget,
            stylePreset,
            backgroundAssetPath,
            jsonText(workflowConstraints),
            toTimestamp(utcNow()),
            projectId
        );
    }

    private void updateRequirementFormAnswers(UUID requirementFormId, JsonNode answers) {
        jdbcTemplate.update(
            "update requirement_forms set answers_json = cast(? as jsonb), status = ?, updated_at = ? where id = ?",
            jsonText(answers),
            STATUS_COMPLETED,
            toTimestamp(utcNow()),
            requirementFormId
        );
    }

    private void updateRequirementFormForOutline(UUID requirementFormId, UUID outlineVersionId, JsonNode outline) {
        jdbcTemplate.update(
            """
            update requirement_forms
            set based_on_outline_version_id = ?, status = ?, outline_context_md = ?, updated_at = ?
            where id = ?
            """,
            outlineVersionId,
            STATUS_COMPLETED,
            outline.path("narrative").asText(""),
            toTimestamp(utcNow()),
            requirementFormId
        );
    }

    private UUID insertOutlineVersion(UUID projectId, JsonNode outline, String status, UUID parentVersionId) {
        Integer nextVersion = jdbcTemplate.queryForObject(
            "select coalesce(max(version_no), 0) + 1 from outline_versions where project_id = ?",
            Integer.class,
            projectId
        );
        UUID id = UUID.randomUUID();
        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into outline_versions (id, project_id, version_no, status, parent_version_id, outline_json, created_at, updated_at)
            values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
            """,
            id,
            projectId,
            nextVersion,
            status,
            parentVersionId,
            jsonText(outline),
            toTimestamp(now),
            toTimestamp(now)
        );
        return id;
    }

    private void syncProjectPages(UUID projectId, JsonNode outline, boolean markDownstreamStale) {
        Map<String, UUID> existingByCode = loadPageIdsByCode(projectId);
        Map<String, Boolean> seen = new HashMap<>();
        int sortOrder = 1;
        for (JsonNode section : iterable(outline.path("sections"))) {
            for (JsonNode page : iterable(section.path("pages"))) {
                String pageCode = page.path("id").asText();
                seen.put(pageCode, true);
                UUID existingPageId = existingByCode.get(pageCode);
                if (existingPageId == null) {
                    insertProjectPage(projectId, pageCode, "content", section.path("title").asText(""), sortOrder);
                } else {
                    jdbcTemplate.update(
                        """
                        update project_pages
                        set page_role = ?, part_title = ?, sort_order = ?, outline_status = ?, updated_at = ?,
                            search_status = case when search_status = ? then ? else search_status end,
                            summary_status = case when summary_status = ? then ? else summary_status end,
                            draft_status = case when draft_status = ? then ? else draft_status end,
                            design_status = case when design_status = ? then ? else design_status end,
                            artifact_staleness_json = cast(? as jsonb)
                        where id = ?
                        """,
                        "content",
                        section.path("title").asText(""),
                        sortOrder,
                        STATUS_READY,
                        toTimestamp(utcNow()),
                        STATUS_PENDING,
                        STATUS_PENDING,
                        STATUS_PENDING,
                        STATUS_PENDING,
                        STATUS_PENDING,
                        markDownstreamStale ? STATUS_STALE : STATUS_PENDING,
                        STATUS_PENDING,
                        markDownstreamStale ? STATUS_STALE : STATUS_PENDING,
                        jsonText(markDownstreamStale ? outlineChangedStaleness() : emptyObject()),
                        existingPageId
                    );
                }
                sortOrder++;
            }
        }

        for (Map.Entry<String, UUID> entry : existingByCode.entrySet()) {
            if (!seen.containsKey(entry.getKey())) {
                jdbcTemplate.update(
                    "update project_pages set outline_status = ?, artifact_staleness_json = cast(? as jsonb), updated_at = ? where id = ?",
                    "REMOVED",
                    jsonText(outlineChangedStaleness()),
                    toTimestamp(utcNow()),
                    entry.getValue()
                );
            }
        }
    }

    private void insertProjectPage(UUID projectId, String pageCode, String pageRole, String partTitle, int sortOrder) {
        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into project_pages (
                id, project_id, page_code, page_role, part_title, sort_order,
                current_brief_version_id, current_research_session_id, current_draft_version_id, current_design_version_id,
                outline_status, search_status, summary_status, draft_status, design_status, artifact_staleness_json, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
            """,
            UUID.randomUUID(),
            projectId,
            pageCode,
            pageRole,
            partTitle,
            sortOrder,
            null,
            null,
            null,
            null,
            STATUS_READY,
            STATUS_PENDING,
            STATUS_PENDING,
            STATUS_PENDING,
            STATUS_PENDING,
            jsonText(emptyObject()),
            toTimestamp(now),
            toTimestamp(now)
        );
    }

    private UUID insertResearchSession(UUID projectId, UUID pageId, JsonNode item) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into research_sessions (
                id, project_id, page_id, scope_type, session_role, page_brief_version_id, based_on_session_id, research_goal,
                query_plan_json, summary_md, key_findings_json, overlap_risks_json, open_questions_json, status, confirmed_by_message_id,
                context_snapshot_json, candidate_sources_json, selected_citations_json, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?)
            """,
            id,
            projectId,
            pageId,
            SCOPE_PAGE,
            "CURRENT",
            null,
            null,
            item.path("searchIntent").asText(""),
            jsonText(item.path("queries")),
            item.path("findings").asText(""),
            jsonText(keyFindings(item)),
            jsonText(emptyObject()),
            jsonText(emptyArray()),
            STATUS_COMPLETED,
            null,
            jsonText(item),
            jsonText(item.path("sources")),
            jsonText(emptyArray()),
            toTimestamp(now),
            toTimestamp(now)
        );
        return id;
    }

    private UUID insertPageBriefVersion(UUID projectId, UUID pageId, String sectionTitle, JsonNode pagePlan, JsonNode researchPayload) {
        Integer nextVersion = jdbcTemplate.queryForObject(
            "select coalesce(max(version_no), 0) + 1 from page_brief_versions where page_id = ?",
            Integer.class,
            pageId
        );
        UUID id = UUID.randomUUID();
        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into page_brief_versions (
                id, project_id, page_id, version_no, status, parent_version_id, section_title, title, content_outline_json,
                content_summary, self_check_result_json, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, cast(? as jsonb), ?, ?)
            """,
            id,
            projectId,
            pageId,
            nextVersion,
            STATUS_COMPLETED,
            null,
            sectionTitle,
            pagePlan.path("title").asText(""),
            jsonText(pagePlan),
            researchPayload.path("findings").asText(""),
            jsonText(objectNode("status", "passed")),
            toTimestamp(now),
            toTimestamp(now)
        );
        return id;
    }

    private UUID insertDraftVersion(UUID projectId, UUID pageId, UUID briefVersionId, UUID researchSessionId, String draftSvg) {
        Integer nextVersion = jdbcTemplate.queryForObject(
            "select coalesce(max(version_no), 0) + 1 from draft_versions where page_id = ?",
            Integer.class,
            pageId
        );
        UUID id = UUID.randomUUID();
        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into draft_versions (id, project_id, page_id, version_no, status, page_brief_version_id, research_session_id, draft_svg_markup, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            projectId,
            pageId,
            nextVersion,
            STATUS_COMPLETED,
            briefVersionId,
            researchSessionId,
            draftSvg,
            toTimestamp(now),
            toTimestamp(now)
        );
        return id;
    }

    private Map<String, UUID> loadPageIdsByCode(UUID projectId) {
        Map<String, UUID> result = new LinkedHashMap<>();
        jdbcTemplate.query("select id, page_code from project_pages where project_id = ? order by sort_order asc", rs -> {
            result.put(rs.getString("page_code"), uuid(rs.getObject("id")));
        }, projectId);
        return result;
    }

    private void persistResearchArtifacts(UUID projectId, UUID pageId, UUID sessionId, JsonNode item) {
        OffsetDateTime now = utcNow();
        UUID collectionId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into source_collections (id, project_id, page_id, collection_type, title, created_at) values (?, ?, ?, ?, ?, ?)",
            collectionId,
            projectId,
            pageId,
            "RESEARCH_SESSION",
            item.path("title").asText("Research"),
            toTimestamp(now)
        );

        int rank = 1;
        int citationIndex = 1;
        for (JsonNode source : iterable(item.path("sources"))) {
            jdbcTemplate.update(
                """
                insert into research_sources (id, research_session_id, title, url, snippet, provider_rank, raw_payload_json, created_at)
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """,
                UUID.randomUUID(),
                sessionId,
                source.path("title").asText(""),
                source.path("url").asText(""),
                source.path("content").asText(""),
                rank,
                jsonText(source),
                toTimestamp(now)
            );

            String normalizedUrl = normalizeUrl(source.path("url").asText(""));
            UUID urlCacheId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                insert into url_content_cache (id, normalized_url, provider, title, markdown_content, metadata_json, content_hash, status, expires_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
                on conflict (normalized_url) do update
                set provider = excluded.provider, title = excluded.title, markdown_content = excluded.markdown_content,
                    metadata_json = excluded.metadata_json, content_hash = excluded.content_hash, status = excluded.status, updated_at = excluded.updated_at
                """,
                urlCacheId,
                normalizedUrl,
                "tavily",
                source.path("title").asText(""),
                source.path("content").asText(""),
                jsonText(source),
                sha256(source.path("content").asText("")),
                STATUS_COMPLETED,
                toTimestamp(now.plusDays(7)),
                toTimestamp(now),
                toTimestamp(now)
            );
            UUID actualUrlCacheId = jdbcTemplate.queryForObject(
                "select id from url_content_cache where normalized_url = ?",
                UUID.class,
                normalizedUrl
            );

            UUID documentId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                insert into source_documents (id, collection_id, source_type, source_uri, url_cache_id, title, markdown_content, metadata_json, content_hash, status, created_at)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
                """,
                documentId,
                collectionId,
                "WEB",
                source.path("url").asText(""),
                actualUrlCacheId,
                source.path("title").asText(""),
                source.path("content").asText(""),
                jsonText(source),
                sha256(source.path("content").asText("")),
                STATUS_COMPLETED,
                toTimestamp(now)
            );

            UUID chunkId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                insert into source_chunks (id, source_document_id, chunk_index, section_path, content_md, content_for_embedding, embedding, token_count, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                chunkId,
                documentId,
                0,
                item.path("title").asText(""),
                source.path("content").asText(""),
                source.path("content").asText(""),
                null,
                approximateTokens(source.path("content").asText("")),
                toTimestamp(now)
            );

            String queryText = item.path("queries").isArray() && item.path("queries").size() > 0
                ? item.path("queries").get(0).asText("")
                : item.path("title").asText("");
            jdbcTemplate.update(
                """
                insert into bocha_search_cache (id, query_key, query_text, provider, result_json, result_count, expires_at, created_at)
                values (?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
                on conflict (query_key) do nothing
                """,
                UUID.randomUUID(),
                sha256(queryText),
                queryText,
                "tavily",
                jsonText(source),
                1,
                toTimestamp(now.plusDays(7)),
                toTimestamp(now)
            );

            UUID retrievalRunId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                insert into retrieval_runs (id, project_id, research_session_id, query_text, retrieval_mode, status, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                retrievalRunId,
                projectId,
                sessionId,
                queryText,
                "web-search",
                STATUS_COMPLETED,
                toTimestamp(now)
            );

            jdbcTemplate.update(
                """
                insert into retrieval_candidates (id, retrieval_run_id, source_document_id, chunk_id, score_vector, score_keyword, score_final, selected)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                retrievalRunId,
                documentId,
                chunkId,
                source.path("score").asDouble(0),
                source.path("score").asDouble(0),
                source.path("score").asDouble(0),
                true
            );

            jdbcTemplate.update(
                """
                insert into research_session_sources (id, research_session_id, source_document_id, chunk_id, rank_no, excerpt_md, relevance_score, usage_note, is_pinned)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                sessionId,
                documentId,
                chunkId,
                rank,
                source.path("content").asText(""),
                source.path("score").asDouble(0),
                "research",
                rank == 1
            );

            jdbcTemplate.update(
                """
                insert into citations (id, project_id, page_id, research_session_id, source_document_id, chunk_id, title, url, excerpt_md, citation_label, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                projectId,
                pageId,
                sessionId,
                documentId,
                chunkId,
                source.path("title").asText(""),
                source.path("url").asText(""),
                source.path("content").asText(""),
                "CIT-" + citationIndex,
                toTimestamp(now)
            );

            rank++;
            citationIndex++;
        }
    }

    private RequirementFormSnapshot loadRequirementForm(UUID projectId) {
        List<RequirementFormSnapshot> forms = jdbcTemplate.query(
            "select * from requirement_forms where project_id = ?",
            (rs, rowNum) -> new RequirementFormSnapshot(
                uuid(rs.getObject("id")),
                rs.getString("status"),
                uuid(rs.getObject("based_on_outline_version_id")),
                rs.getString("summary_md"),
                rs.getString("outline_context_md"),
                json(rs.getString("fixed_items_json")),
                json(rs.getString("init_search_queries_json")),
                json(rs.getString("init_search_results_json")),
                json(rs.getString("init_corpus_digest_json")),
                json(rs.getString("ai_questions_json")),
                json(rs.getString("answers_json")),
                offset(rs.getTimestamp("created_at")),
                offset(rs.getTimestamp("updated_at"))
            ),
            projectId
        );
        if (forms.isEmpty()) {
            throw new NotFoundException("需求表不存在: " + projectId);
        }
        return forms.get(0);
    }

    private OutlineVersionSnapshot loadOutlineVersion(UUID outlineId) {
        return jdbcTemplate.queryForObject(
            "select * from outline_versions where id = ?",
            (rs, rowNum) -> new OutlineVersionSnapshot(
                uuid(rs.getObject("id")),
                rs.getInt("version_no"),
                rs.getString("status"),
                uuid(rs.getObject("parent_version_id")),
                json(rs.getString("outline_json")),
                offset(rs.getTimestamp("created_at")),
                offset(rs.getTimestamp("updated_at"))
            ),
            outlineId
        );
    }

    private OutlineVersionSnapshot loadCurrentOutline(UUID projectId) {
        UUID outlineId = jdbcTemplate.queryForObject(
            "select current_outline_version_id from projects where id = ?",
            UUID.class,
            projectId
        );
        if (outlineId == null) {
            throw new NotFoundException("当前项目还没有 outline");
        }
        return loadOutlineVersion(outlineId);
    }

    private List<ProjectPageSnapshot> loadPages(UUID projectId) {
        List<ProjectPageSnapshot> pages = jdbcTemplate.query(
            "select * from project_pages where project_id = ? order by sort_order asc",
            (rs, rowNum) -> mapPage(rs),
            projectId
        );
        pages.sort(Comparator.comparing(ProjectPageSnapshot::sortOrder));
        return pages;
    }

    private List<ProjectMessageSnapshot> loadMessages(UUID projectId) {
        return jdbcTemplate.query(
            "select * from project_messages where project_id = ? order by created_at asc",
            (rs, rowNum) -> new ProjectMessageSnapshot(
                uuid(rs.getObject("id")),
                rs.getString("stage"),
                rs.getString("scope_type"),
                uuid(rs.getObject("target_page_id")),
                rs.getString("role"),
                rs.getString("content_md"),
                json(rs.getString("structured_payload_json")),
                offset(rs.getTimestamp("created_at"))
            ),
            projectId
        );
    }

    private List<StageRunSnapshot> loadProjectRuns(UUID projectId) {
        return jdbcTemplate.query(
            "select * from project_stage_runs where project_id = ? order by started_at desc",
            (rs, rowNum) -> new StageRunSnapshot(
                uuid(rs.getObject("id")),
                rs.getString("stage"),
                rs.getInt("attempt_no"),
                rs.getString("status"),
                json(rs.getString("input_refs_json")),
                json(rs.getString("output_ref_json")),
                rs.getString("error_message"),
                offset(rs.getTimestamp("started_at")),
                offset(rs.getTimestamp("finished_at"))
            ),
            projectId
        );
    }

    private ProjectPageSnapshot mapPage(ResultSet rs) throws SQLException {
        UUID pageId = uuid(rs.getObject("id"));
        UUID currentBriefVersionId = uuid(rs.getObject("current_brief_version_id"));
        UUID currentResearchSessionId = uuid(rs.getObject("current_research_session_id"));
        UUID currentDraftVersionId = uuid(rs.getObject("current_draft_version_id"));
        UUID currentDesignVersionId = uuid(rs.getObject("current_design_version_id"));
        return new ProjectPageSnapshot(
            pageId,
            rs.getString("page_code"),
            rs.getString("page_role"),
            rs.getString("part_title"),
            rs.getInt("sort_order"),
            currentBriefVersionId,
            currentResearchSessionId,
            currentDraftVersionId,
            currentDesignVersionId,
            rs.getString("outline_status"),
            rs.getString("search_status"),
            rs.getString("summary_status"),
            rs.getString("draft_status"),
            rs.getString("design_status"),
            json(rs.getString("artifact_staleness_json")),
            loadBriefPayload(currentBriefVersionId),
            loadResearchPayload(currentResearchSessionId),
            loadDraftSvg(currentDraftVersionId),
            loadDesignSvg(currentDesignVersionId),
            loadCitationPayloads(pageId),
            offset(rs.getTimestamp("created_at")),
            offset(rs.getTimestamp("updated_at"))
        );
    }

    private JsonNode loadBriefPayload(UUID briefVersionId) {
        if (briefVersionId == null) {
            return emptyObject();
        }
        List<String> rows = jdbcTemplate.query(
            "select content_outline_json from page_brief_versions where id = ?",
            singleStringMapper(),
            briefVersionId
        );
        return rows.isEmpty() ? emptyObject() : json(rows.get(0));
    }

    private String loadDraftSvg(UUID draftVersionId) {
        if (draftVersionId == null) {
            return null;
        }
        List<String> rows = jdbcTemplate.query(
            "select draft_svg_markup from draft_versions where id = ?",
            singleStringMapper(),
            draftVersionId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String loadDesignSvg(UUID designVersionId) {
        if (designVersionId == null) {
            return null;
        }
        List<String> rows = jdbcTemplate.query(
            "select design_svg_markup from design_versions where id = ?",
            singleStringMapper(),
            designVersionId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private JsonNode loadResearchPayload(UUID researchSessionId) {
        if (researchSessionId == null) {
            return emptyObject();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from research_sessions where id = ?", researchSessionId);
        if (rows.isEmpty()) {
            return emptyObject();
        }
        Map<String, Object> row = rows.get(0);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("pageId", text(row.get("page_id")));
        node.put("searchIntent", text(row.get("research_goal")));
        node.put("findings", text(row.get("summary_md")));
        node.set("queries", json(row.get("query_plan_json")));
        node.set("sources", loadResearchSourcePayloads(researchSessionId));
        node.set("citations", loadCitationArray(researchSessionId));
        return node;
    }

    private ArrayNode loadResearchSourcePayloads(UUID researchSessionId) {
        ArrayNode array = objectMapper.createArrayNode();
        jdbcTemplate.query(
            "select title, url, snippet, provider_rank, raw_payload_json from research_sources where research_session_id = ? order by provider_rank asc",
            rs -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("title", rs.getString("title"));
                node.put("url", rs.getString("url"));
                node.put("content", rs.getString("snippet"));
                node.put("providerRank", rs.getInt("provider_rank"));
                node.set("rawPayload", json(rs.getString("raw_payload_json")));
                array.add(node);
            },
            researchSessionId
        );
        return array;
    }

    private ArrayNode loadCitationArray(UUID researchSessionId) {
        ArrayNode array = objectMapper.createArrayNode();
        jdbcTemplate.query(
            "select title, url, excerpt_md, citation_label from citations where research_session_id = ? order by citation_label asc",
            rs -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("title", rs.getString("title"));
                node.put("url", rs.getString("url"));
                node.put("excerpt", rs.getString("excerpt_md"));
                node.put("label", rs.getString("citation_label"));
                array.add(node);
            },
            researchSessionId
        );
        return array;
    }

    private List<JsonNode> loadCitationPayloads(UUID pageId) {
        return jdbcTemplate.query(
            "select title, url, excerpt_md, citation_label from citations where page_id = ? order by citation_label asc",
            (rs, rowNum) -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("title", rs.getString("title"));
                node.put("url", rs.getString("url"));
                node.put("excerpt", rs.getString("excerpt_md"));
                node.put("label", rs.getString("citation_label"));
                return (JsonNode) node;
            },
            pageId
        );
    }

    private UUID insertDesignVersion(UUID projectId, UUID pageId, UUID draftVersionId, String stylePackId, String backgroundAssetPath, String designSvg) {
        Integer nextVersion = jdbcTemplate.queryForObject(
            "select coalesce(max(version_no), 0) + 1 from design_versions where page_id = ?",
            Integer.class,
            pageId
        );
        UUID id = UUID.randomUUID();
        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into design_versions (id, project_id, page_id, version_no, status, draft_version_id, style_pack_id, background_asset_path, design_svg_markup, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            projectId,
            pageId,
            nextVersion,
            STATUS_COMPLETED,
            draftVersionId,
            stylePackId,
            backgroundAssetPath,
            designSvg,
            toTimestamp(now),
            toTimestamp(now)
        );
        return id;
    }

    private void updatePageHeadsAndStatuses(
        UUID pageId,
        UUID briefVersionId,
        UUID researchSessionId,
        UUID draftVersionId,
        UUID designVersionId,
        String outlineStatus,
        String searchStatus,
        String summaryStatus,
        String draftStatus,
        String designStatus,
        JsonNode artifactStaleness
    ) {
        jdbcTemplate.update(
            """
            update project_pages
            set current_brief_version_id = coalesce(?, current_brief_version_id),
                current_research_session_id = coalesce(?, current_research_session_id),
                current_draft_version_id = coalesce(?, current_draft_version_id),
                current_design_version_id = coalesce(?, current_design_version_id),
                outline_status = coalesce(?, outline_status),
                search_status = coalesce(?, search_status),
                summary_status = coalesce(?, summary_status),
                draft_status = coalesce(?, draft_status),
                design_status = coalesce(?, design_status),
                artifact_staleness_json = cast(? as jsonb),
                updated_at = ?
            where id = ?
            """,
            briefVersionId,
            researchSessionId,
            draftVersionId,
            designVersionId,
            outlineStatus,
            searchStatus,
            summaryStatus,
            draftStatus,
            designStatus,
            jsonText(artifactStaleness),
            toTimestamp(utcNow()),
            pageId
        );
    }

    private UUID startProjectStageRun(UUID projectId, String stage, JsonNode inputRefs) {
        Integer attempt = jdbcTemplate.queryForObject(
            "select coalesce(max(attempt_no), 0) + 1 from project_stage_runs where project_id = ? and stage = ?",
            Integer.class,
            projectId,
            stage
        );
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into project_stage_runs (id, project_id, stage, attempt_no, status, input_refs_json, output_ref_json, error_message, started_at, finished_at)
            values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
            """,
            id,
            projectId,
            stage,
            attempt,
            STATUS_PROCESSING,
            jsonText(inputRefs),
            jsonText(null),
            null,
            toTimestamp(utcNow()),
            null
        );
        return id;
    }

    private void finishProjectStageRun(UUID runId, String status, JsonNode outputRef, String errorMessage) {
        jdbcTemplate.update(
            "update project_stage_runs set status = ?, output_ref_json = cast(? as jsonb), error_message = ?, finished_at = ? where id = ?",
            status,
            jsonText(outputRef),
            errorMessage,
            toTimestamp(utcNow()),
            runId
        );
    }

    private UUID startPageStageRun(UUID projectId, UUID pageId, String stage, JsonNode inputRefs) {
        Integer attempt = jdbcTemplate.queryForObject(
            "select coalesce(max(attempt_no), 0) + 1 from page_stage_runs where page_id = ? and stage = ?",
            Integer.class,
            pageId,
            stage
        );
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into page_stage_runs (id, project_id, page_id, stage, attempt_no, status, input_refs_json, output_ref_json, error_message, started_at, finished_at)
            values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
            """,
            id,
            projectId,
            pageId,
            stage,
            attempt,
            STATUS_PROCESSING,
            jsonText(inputRefs),
            jsonText(null),
            null,
            toTimestamp(utcNow()),
            null
        );
        return id;
    }

    private void finishPageStageRun(UUID runId, String status, JsonNode outputRef, String errorMessage) {
        jdbcTemplate.update(
            "update page_stage_runs set status = ?, output_ref_json = cast(? as jsonb), error_message = ?, finished_at = ? where id = ?",
            status,
            jsonText(outputRef),
            errorMessage,
            toTimestamp(utcNow()),
            runId
        );
    }

    private void appendProjectMessage(UUID projectId, String stage, String scopeType, UUID targetPageId, String role, String content, JsonNode payload) {
        jdbcTemplate.update(
            """
            insert into project_messages (id, project_id, stage, scope_type, target_page_id, role, content_md, structured_payload_json, created_at)
            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """,
            UUID.randomUUID(),
            projectId,
            stage,
            scopeType,
            targetPageId,
            role,
            content,
            jsonText(payload),
            toTimestamp(utcNow())
        );
    }

    private void appendProjectEvent(UUID projectId, String eventType, String stage, String scopeType, UUID targetPageId, UUID agentRunId, JsonNode payload) {
        jdbcTemplate.update(
            """
            insert into project_events (event_id, project_id, event_type, stage, scope_type, target_page_id, agent_run_id, payload_json, created_at)
            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """,
            UUID.randomUUID(),
            projectId,
            eventType,
            stage,
            scopeType,
            targetPageId,
            agentRunId,
            jsonText(payload),
            toTimestamp(utcNow())
        );
    }

    private JsonNode fixedItemsForCreate(CreateStudioProjectRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        if (request.pageCountTarget() != null) {
            node.put("pageCountTarget", request.pageCountTarget());
        }
        if (request.stylePreset() != null) {
            node.put("stylePreset", request.stylePreset());
        }
        if (request.backgroundAssetPath() != null) {
            node.put("backgroundAssetPath", request.backgroundAssetPath());
        }
        if (request.workflowConstraints() != null) {
            node.set("workflowConstraints", request.workflowConstraints());
        }
        return node;
    }

    private JsonNode initialDiscoveryInput(String prompt, String templateId, CreateStudioProjectRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("prompt", prompt);
        node.put("templateId", templateId);
        if (request.pageCountTarget() != null) {
            node.put("pageCountTarget", request.pageCountTarget());
        }
        if (request.stylePreset() != null && !request.stylePreset().isBlank()) {
            node.put("stylePreset", request.stylePreset().trim());
        }
        if (request.backgroundAssetPath() != null && !request.backgroundAssetPath().isBlank()) {
            node.put("backgroundAssetPath", request.backgroundAssetPath().trim());
        }
        if (request.workflowConstraints() != null) {
            node.set("workflowConstraints", request.workflowConstraints());
        }
        return node;
    }

    private JsonNode initialDiscoveryOutput(UUID requirementFormId, JsonNode backgroundSummary, JsonNode discoveryCard) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("requirementFormId", requirementFormId.toString());
        node.put("sourceCount", sourceCount(backgroundSummary));
        node.put("questionCount", discoveryCard.path("questions").size());
        node.put("summaryReady", !backgroundSummary.path("summary").asText("").isBlank());
        return node;
    }

    private JsonNode initialDiscoveryPayload(JsonNode backgroundSummary, JsonNode discoveryCard) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("backgroundSummary", nullSafe(backgroundSummary));
        node.set("discoveryCard", nullSafe(discoveryCard));
        return node;
    }

    private JsonNode projectCreatedPayload(UUID projectId, String templateId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("projectId", projectId.toString());
        node.put("templateId", templateId);
        return node;
    }

    private JsonNode backgroundCompletedPayload(UUID requirementFormId, JsonNode backgroundSummary) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("requirementFormId", requirementFormId.toString());
        node.put("sourceCount", sourceCount(backgroundSummary));
        return node;
    }

    private JsonNode buildDiscoveryAnswers(ProjectStudioCommandRequest request) {
        ObjectNode answers = objectMapper.createObjectNode();
        ArrayNode selected = answers.putArray("selectedOptionIds");
        List<String> ids = request.selectedOptionIds() == null ? List.of() : request.selectedOptionIds();
        ids.forEach(selected::add);
        answers.put("freeformAnswer", request.freeformAnswer() == null ? "" : request.freeformAnswer().trim());
        return answers;
    }

    private String messageForAnswers(JsonNode answers) {
        if (!answers.path("freeformAnswer").asText("").isBlank()) {
            return answers.path("freeformAnswer").asText("");
        }
        return answers.path("selectedOptionIds").toString();
    }

    private JsonNode backgroundDigest(JsonNode backgroundSummary) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("summary", backgroundSummary.path("summary").asText(""));
        node.put("topicUnderstanding", backgroundSummary.path("topicUnderstanding").asText(""));
        return node;
    }

    private int sourceCount(JsonNode backgroundSummary) {
        return backgroundSummary != null && backgroundSummary.path("sources").isArray()
            ? backgroundSummary.path("sources").size()
            : 0;
    }

    private JsonNode keyFindings(JsonNode item) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", item.path("title").asText(""));
        node.put("needsSearch", item.path("needsSearch").asBoolean(false));
        node.put("sourceCount", item.path("sources").size());
        return node;
    }

    private JsonNode findOutlinePage(JsonNode outline, String pageCode) {
        for (JsonNode section : iterable(outline.path("sections"))) {
            for (JsonNode page : iterable(section.path("pages"))) {
                if (pageCode.equals(page.path("id").asText())) {
                    return page;
                }
            }
        }
        return emptyObject();
    }

    private JsonNode normalizePagePlan(JsonNode pagePlan, JsonNode outlinePage, JsonNode researchPayload) {
        if (pagePlan != null && pagePlan.has("cards") && pagePlan.path("cards").isArray() && pagePlan.path("cards").size() > 0) {
            return pagePlan;
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("pageId", outlinePage.path("id").asText(UUID.randomUUID().toString()));
        node.put("title", outlinePage.path("title").asText("未命名页面"));
        node.put("goal", researchPayload.path("findings").asText(outlinePage.path("intent").asText("解释当前页面的核心意图")));
        node.put("layout", "bento-grid");
        node.put("visualTone", "clean");
        node.put("speakerNotes", researchPayload.path("findings").asText("围绕当前页面的主题展开说明。"));
        ArrayNode cards = node.putArray("cards");
        ObjectNode card = cards.addObject();
        card.put("id", "card-1");
        card.put("kind", "text");
        card.put("heading", outlinePage.path("title").asText("核心标题"));
        card.put("body", researchPayload.path("findings").asText("当前页面暂无更详细 research，可在后续继续补充。"));
        card.put("emphasis", "medium");
        return node;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        List<JsonNode> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(items::add);
        }
        return items;
    }

    private JsonNode nullSafe(JsonNode node) {
        return node == null ? emptyObject() : node;
    }

    private boolean looksLikeContinue(String normalized) {
        return normalized.contains("继续") || normalized.contains("下一步") || normalized.equals("ok") || normalized.equals("好的");
    }

    private Integer derivePageCountTarget(JsonNode answers) {
        for (JsonNode selected : iterable(answers.path("selectedOptionIds"))) {
            String value = selected.asText();
            if ("count-5-10".equals(value)) {
                return 8;
            }
            if ("count-10-15".equals(value)) {
                return 12;
            }
            if ("count-15-20".equals(value)) {
                return 16;
            }
        }
        return null;
    }

    private Integer currentPageCountTarget(UUID projectId) {
        return jdbcTemplate.queryForObject("select page_count_target from projects where id = ?", Integer.class, projectId);
    }

    private String currentStylePreset(UUID projectId, String fallback) {
        String value = jdbcTemplate.queryForObject("select style_preset from projects where id = ?", String.class, projectId);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String currentBackgroundAsset(UUID projectId) {
        return jdbcTemplate.queryForObject("select background_asset_path from projects where id = ?", String.class, projectId);
    }

    private JsonNode currentWorkflowConstraints(UUID projectId) {
        List<String> rows = jdbcTemplate.query(
            "select workflow_constraints_json from projects where id = ?",
            singleStringMapper(),
            projectId
        );
        return rows.isEmpty() ? null : json(rows.get(0));
    }

    private String currentRequestText(UUID projectId) {
        return jdbcTemplate.queryForObject("select request_text from projects where id = ?", String.class, projectId);
    }

    private JsonNode staleResearchResolved() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("researchStale", false);
        node.put("draftStale", true);
        node.put("designStale", true);
        return node;
    }

    private JsonNode planningStaleness() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("researchStale", false);
        node.put("draftStale", false);
        node.put("designStale", true);
        return node;
    }

    private JsonNode outlineChangedStaleness() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("researchStale", true);
        node.put("draftStale", true);
        node.put("designStale", true);
        return node;
    }

    private JsonNode clearStaleness() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("researchStale", false);
        node.put("draftStale", false);
        node.put("designStale", false);
        return node;
    }

    private JsonNode emptyArray() {
        return objectMapper.createArrayNode();
    }

    private JsonNode emptyObject() {
        return objectMapper.createObjectNode();
    }

    private JsonNode textPayload(String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("text", text);
        return node;
    }

    private JsonNode errorPayload(Exception exception) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("message", exception.getMessage());
        return node;
    }

    private JsonNode objectNode(String key, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(key, value);
        return node;
    }

    private String scopeOf(UUID targetPageId) {
        return targetPageId == null ? SCOPE_PROJECT : SCOPE_PAGE;
    }

    private String jsonText(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.nullNode() : node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON 序列化失败", exception);
        }
    }

    private JsonNode json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value.toString());
        } catch (JsonProcessingException exception) {
            return textPayload(value.toString());
        }
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        return Integer.parseInt(value.toString());
    }

    private OffsetDateTime offset(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value.toString());
    }

    private Timestamp toTimestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private OffsetDateTime utcNow() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeUrl(String url) {
        return url == null ? "" : url.trim().toLowerCase();
    }

    private int approximateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.length() / 4);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private RowMapper<String> singleStringMapper() {
        return (rs, rowNum) -> rs.getString(1);
    }
}
