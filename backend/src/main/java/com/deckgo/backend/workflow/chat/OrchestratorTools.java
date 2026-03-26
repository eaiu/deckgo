package com.deckgo.backend.workflow.chat;

import com.deckgo.backend.ai.service.OutlineAgentService;
import com.deckgo.backend.ai.service.PagePlanAgentService;
import com.deckgo.backend.ai.service.ResearchAgentService;
import com.deckgo.backend.ai.service.SvgDesignAgentService;
import com.deckgo.backend.ai.service.TavilySearchService;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.workflow.entity.WorkflowPageEntity;
import com.deckgo.backend.workflow.entity.WorkflowSessionEntity;
import com.deckgo.backend.workflow.entity.WorkflowVersionEntity;
import com.deckgo.backend.workflow.enums.WorkflowSessionStatus;
import com.deckgo.backend.workflow.enums.WorkflowStage;
import com.deckgo.backend.workflow.enums.WorkflowVersionSource;
import com.deckgo.backend.workflow.repository.WorkflowPageRepository;
import com.deckgo.backend.workflow.repository.WorkflowSessionRepository;
import com.deckgo.backend.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrchestratorTools {

    private final OutlineAgentService outlineAgentService;
    private final ResearchAgentService researchAgentService;
    private final PagePlanAgentService pagePlanAgentService;
    private final SvgDesignAgentService svgDesignAgentService;
    private final TavilySearchService tavilySearchService;
    private final ProjectService projectService;
    private final WorkflowSessionRepository sessionRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowPageRepository pageRepository;
    private final ToolProgressEmitter progressEmitter;
    private final ObjectMapper objectMapper;

    public OrchestratorTools(
        OutlineAgentService outlineAgentService,
        ResearchAgentService researchAgentService,
        PagePlanAgentService pagePlanAgentService,
        SvgDesignAgentService svgDesignAgentService,
        TavilySearchService tavilySearchService,
        ProjectService projectService,
        WorkflowSessionRepository sessionRepository,
        WorkflowVersionRepository versionRepository,
        WorkflowPageRepository pageRepository,
        ToolProgressEmitter progressEmitter,
        ObjectMapper objectMapper
    ) {
        this.outlineAgentService = outlineAgentService;
        this.researchAgentService = researchAgentService;
        this.pagePlanAgentService = pagePlanAgentService;
        this.svgDesignAgentService = svgDesignAgentService;
        this.tavilySearchService = tavilySearchService;
        this.projectService = projectService;
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.pageRepository = pageRepository;
        this.progressEmitter = progressEmitter;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "根据用户的需求回答和背景信息生成 PPT 大纲。当用户确认了调研问题后调用。")
    public String generateOutline(
        @ToolParam(description = "会话 ID") String sessionId,
        @ToolParam(description = "用户回答的 JSON，包含 selectedOptionIds 和 freeformAnswer") String discoveryAnswersJson
    ) {
        UUID sid = UUID.fromString(sessionId);
        emitProgress(sid, "generateOutline", "started", "正在生成大纲...");

        WorkflowSessionEntity session = sessionRepository.findById(sid).orElseThrow();
        ProjectEntity project = projectService.findEntity(session.getProjectId());

        JsonNode answers;
        try {
            answers = objectMapper.readTree(discoveryAnswersJson);
        } catch (Exception e) {
            answers = session.getDiscoveryAnswersJson();
        }

        session.setDiscoveryAnswersJson(answers);
        JsonNode outline = outlineAgentService.generateOutline(project, session.getBackgroundJson(), answers);
        session.setOutlineJson(outline);
        session.setPageResearchJson(null);
        session.setCurrentVersionId(null);
        session.setCurrentStage(WorkflowStage.OUTLINE);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);

        if (outline.hasNonNull("title")) {
            project.setTitle(outline.path("title").asText(project.getTitle()));
            projectService.saveEntity(project);
        }
        sessionRepository.save(session);

        int sectionCount = outline.has("sections") ? outline.path("sections").size() : 0;
        int pageCount = 0;
        if (outline.has("sections")) {
            for (JsonNode section : outline.path("sections")) {
                pageCount += section.has("pages") ? section.path("pages").size() : 0;
            }
        }

        emitProgress(sid, "generateOutline", "completed", "大纲生成完成");
        return "大纲已生成：标题「%s」，%d 个章节，共 %d 页".formatted(
            outline.path("title").asText(""), sectionCount, pageCount);
    }

    @Tool(description = "根据用户反馈修改现有大纲。当用户想要调整大纲结构、添加/删除章节或页面时调用。")
    public String reviseOutline(
        @ToolParam(description = "会话 ID") String sessionId,
        @ToolParam(description = "用户对大纲的修改意见") String feedback
    ) {
        UUID sid = UUID.fromString(sessionId);
        emitProgress(sid, "reviseOutline", "started", "正在修改大纲...");

        WorkflowSessionEntity session = sessionRepository.findById(sid).orElseThrow();
        ProjectEntity project = projectService.findEntity(session.getProjectId());

        JsonNode revisedOutline = outlineAgentService.reviseOutline(
            project,
            session.getBackgroundJson(),
            session.getDiscoveryAnswersJson(),
            session.getOutlineJson(),
            feedback
        );
        session.setOutlineJson(revisedOutline);
        session.setPageResearchJson(null);
        session.setCurrentVersionId(null);
        sessionRepository.save(session);

        emitProgress(sid, "reviseOutline", "completed", "大纲修改完成");
        return "大纲已根据反馈更新：「%s」".formatted(revisedOutline.path("title").asText(""));
    }

    @Tool(description = "对大纲中的每一页搜索补充资料。在大纲确认后调用。")
    public String generatePageResearch(
        @ToolParam(description = "会话 ID") String sessionId
    ) {
        UUID sid = UUID.fromString(sessionId);
        emitProgress(sid, "generatePageResearch", "started", "正在逐页搜索资料...");

        WorkflowSessionEntity session = sessionRepository.findById(sid).orElseThrow();
        ProjectEntity project = projectService.findEntity(session.getProjectId());

        JsonNode pageResearch = researchAgentService.generatePageResearch(
            project,
            session.getBackgroundJson(),
            session.getDiscoveryAnswersJson(),
            session.getOutlineJson()
        );

        session.setPageResearchJson(pageResearch);
        session.setCurrentStage(WorkflowStage.RESEARCH);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);
        sessionRepository.save(session);

        int sourceCount = 0;
        if (pageResearch.isArray()) {
            for (JsonNode item : pageResearch) {
                sourceCount += item.has("sources") ? item.path("sources").size() : 0;
            }
        }

        emitProgress(sid, "generatePageResearch", "completed",
            "资料搜集完成（%d 条来源）".formatted(sourceCount));
        return "逐页资料搜集完成，共 %d 页、%d 条来源".formatted(
            pageResearch.isArray() ? pageResearch.size() : 0, sourceCount);
    }

    @Tool(description = "将研究成果转化为页面策划稿并生成草稿 SVG。在资料搜集后调用。")
    public String generatePagePlans(
        @ToolParam(description = "会话 ID") String sessionId
    ) {
        UUID sid = UUID.fromString(sessionId);
        emitProgress(sid, "generatePagePlans", "started", "正在生成页面策划稿...");

        WorkflowSessionEntity session = sessionRepository.findById(sid).orElseThrow();
        ProjectEntity project = projectService.findEntity(session.getProjectId());
        JsonNode outline = session.getOutlineJson();

        // Collect outline pages
        List<JsonNode> outlinePages = new ArrayList<>();
        if (outline.has("sections")) {
            for (JsonNode section : outline.path("sections")) {
                for (JsonNode page : section.path("pages")) {
                    outlinePages.add(page);
                }
            }
        }

        // Create version and point session to it immediately
        WorkflowVersionEntity version = createVersion(
            project.getId(), session.getSelectedTemplateId(),
            session.getBackgroundJson(), session.getPageResearchJson(),
            outline, WorkflowVersionSource.PLANNING, "自动生成页面策划稿"
        );
        session.setCurrentVersionId(version.getId());
        session.setCurrentStage(WorkflowStage.PLANNING);
        sessionRepository.save(session);

        // Build sub-steps for progress tracking
        List<ToolSubStep> subSteps = new ArrayList<>();
        for (int i = 0; i < outlinePages.size(); i++) {
            subSteps.add(new ToolSubStep(
                "第%d页: %s".formatted(i + 1, outlinePages.get(i).path("title").asText("未命名")),
                "pending"
            ));
        }

        String outlineTitle = outline.path("title").asText("");
        int completed = 0;

        // Generate each page: PagePlan → save → draft SVG → save → emit progress
        for (int i = 0; i < outlinePages.size(); i++) {
            JsonNode outlinePage = outlinePages.get(i);
            String pageId = outlinePage.path("id").asText("");

            subSteps.set(i, new ToolSubStep(subSteps.get(i).label(), "in_progress"));
            emitProgress(sid, "generatePagePlans", "started",
                "正在生成第 %d/%d 页策划稿...".formatted(i + 1, outlinePages.size()), subSteps);

            // 1. Generate PagePlan for this single page
            JsonNode pagePlan = pagePlanAgentService.generateSinglePagePlan(
                project,
                session.getBackgroundJson(),
                outlinePage,
                findPageResearch(pageId, session.getPageResearchJson()),
                outlineTitle,
                session.getSelectedTemplateId()
            );

            // 2. Save page to DB immediately (without SVG first, so poll shows it)
            WorkflowPageEntity pageEntity = new WorkflowPageEntity();
            pageEntity.setId(UUID.randomUUID());
            pageEntity.setWorkflowVersionId(version.getId());
            pageEntity.setOrderIndex(i + 1);
            pageEntity.setTitle(pagePlan.path("title").asText("未命名页面"));
            pageEntity.setPagePlanJson(pagePlan);
            pageRepository.save(pageEntity);

            // 3. Generate draft SVG
            String draftSvg = svgDesignAgentService.generateDraftSvg(
                pagePlan,
                findPageResearch(pageId, session.getPageResearchJson()),
                session.getSelectedTemplateId()
            );

            // 4. Update page with SVG
            pageEntity.setDraftSvg(draftSvg);
            pageRepository.save(pageEntity);

            completed++;
            subSteps.set(i, new ToolSubStep(subSteps.get(i).label(), "completed"));
            emitProgress(sid, "generatePagePlans", "started",
                "已完成 %d/%d 页".formatted(completed, outlinePages.size()), subSteps);
        }

        emitProgress(sid, "generatePagePlans", "completed",
            "策划稿生成完成（%d 页）".formatted(completed));
        return "页面策划稿已生成，共 %d 页，每页都包含草稿 SVG".formatted(completed);
    }

    @Tool(description = "生成最终精修 SVG 设计稿。在策划稿确认后调用。")
    public String generateFinalDesign(
        @ToolParam(description = "会话 ID") String sessionId
    ) {
        UUID sid = UUID.fromString(sessionId);
        emitProgress(sid, "generateFinalDesign", "started", "正在生成最终设计稿...");

        WorkflowSessionEntity session = sessionRepository.findById(sid).orElseThrow();
        ProjectEntity project = projectService.findEntity(session.getProjectId());

        if (session.getCurrentVersionId() == null) {
            return "错误：当前会话还没有策划版本，请先生成策划稿";
        }

        List<WorkflowPageEntity> planningPages = pageRepository
            .findByWorkflowVersionIdOrderByOrderIndexAsc(session.getCurrentVersionId());
        if (planningPages.isEmpty()) {
            return "错误：当前策划版本没有页面数据";
        }

        WorkflowVersionEntity designVersion = createVersion(
            project.getId(), session.getSelectedTemplateId(),
            session.getBackgroundJson(), session.getPageResearchJson(),
            session.getOutlineJson(), WorkflowVersionSource.DESIGN, "生成最终 SVG 设计稿"
        );

        // Point session to new version immediately so polls show pages as they're created
        session.setCurrentVersionId(designVersion.getId());
        session.setCurrentStage(WorkflowStage.DESIGN);
        sessionRepository.save(session);

        List<ToolSubStep> subSteps = new ArrayList<>();
        for (WorkflowPageEntity p : planningPages) {
            subSteps.add(new ToolSubStep(
                "第%d页: %s".formatted(p.getOrderIndex(), p.getTitle()), "pending"
            ));
        }

        JsonNode pageResearchJson = session.getPageResearchJson();
        String templateId = session.getSelectedTemplateId();
        int completed = 0;

        // Generate each page: copy from planning → generate final SVG → save → emit progress
        for (int i = 0; i < planningPages.size(); i++) {
            WorkflowPageEntity planningPage = planningPages.get(i);

            subSteps.set(i, new ToolSubStep(subSteps.get(i).label(), "in_progress"));
            emitProgress(sid, "generateFinalDesign", "started",
                "正在精修第 %d/%d 页...".formatted(i + 1, planningPages.size()), subSteps);

            // 1. Save page with draft SVG first so poll shows it immediately
            WorkflowPageEntity page = new WorkflowPageEntity();
            page.setId(UUID.randomUUID());
            page.setWorkflowVersionId(designVersion.getId());
            page.setOrderIndex(planningPage.getOrderIndex());
            page.setTitle(planningPage.getTitle());
            page.setPagePlanJson(planningPage.getPagePlanJson());
            page.setDraftSvg(planningPage.getDraftSvg());
            pageRepository.save(page);

            // 2. Generate final SVG
            String finalSvg = svgDesignAgentService.generateFinalSvg(
                planningPage.getPagePlanJson(),
                findPageResearch(planningPage.getPagePlanJson().path("pageId").asText(""),
                    pageResearchJson),
                templateId
            );

            // 3. Update page with final SVG
            page.setFinalSvg(finalSvg);
            pageRepository.save(page);

            completed++;
            subSteps.set(i, new ToolSubStep(subSteps.get(i).label(), "completed"));
            emitProgress(sid, "generateFinalDesign", "started",
                "已完成 %d/%d 页".formatted(completed, planningPages.size()), subSteps);
        }

        // Mark as completed
        session = sessionRepository.findById(sid).orElse(session);
        session.setStatus(WorkflowSessionStatus.COMPLETED);
        sessionRepository.save(session);

        emitProgress(sid, "generateFinalDesign", "completed",
            "最终设计稿生成完成（%d 页）".formatted(completed));
        return "最终 SVG 设计稿已生成，共 %d 页，可导出".formatted(completed);
    }

    @Tool(description = "修改单页的 SVG 设计。当用户要求修改某一页的布局、内容或视觉效果时调用。")
    public String redesignPage(
        @ToolParam(description = "会话 ID") String sessionId,
        @ToolParam(description = "需要修改的页面 ID") String pageId,
        @ToolParam(description = "用户的修改指令") String instruction
    ) {
        UUID sid = UUID.fromString(sessionId);
        emitProgress(sid, "redesignPage", "started", "正在重新设计页面...");

        WorkflowSessionEntity session = sessionRepository.findById(sid).orElseThrow();
        if (session.getCurrentVersionId() == null) {
            return "错误：当前会话还没有设计版本";
        }

        List<WorkflowPageEntity> pages = pageRepository
            .findByWorkflowVersionIdOrderByOrderIndexAsc(session.getCurrentVersionId());
        WorkflowPageEntity targetPage = pages.stream()
            .filter(p -> p.getId().toString().equals(pageId))
            .findFirst()
            .orElse(null);

        if (targetPage == null) {
            return "错误：找不到页面 " + pageId;
        }

        String newSvg = svgDesignAgentService.generateFinalSvg(
            targetPage.getPagePlanJson(),
            findPageResearch(targetPage.getPagePlanJson().path("pageId").asText(""),
                session.getPageResearchJson()),
            session.getSelectedTemplateId()
        );

        if (targetPage.getFinalSvg() != null) {
            targetPage.setFinalSvg(newSvg);
        } else {
            targetPage.setDraftSvg(newSvg);
        }
        pageRepository.save(targetPage);

        emitProgress(sid, "redesignPage", "completed", "页面重新设计完成");
        return "已重新设计第 %d 页「%s」".formatted(targetPage.getOrderIndex(), targetPage.getTitle());
    }

    @Tool(description = "获取当前会话的完整状态摘要。当你不确定当前进度时调用。")
    public String getSessionContext(
        @ToolParam(description = "会话 ID") String sessionId
    ) {
        UUID sid = UUID.fromString(sessionId);
        WorkflowSessionEntity session = sessionRepository.findById(sid).orElseThrow();
        ProjectEntity project = projectService.findEntity(session.getProjectId());

        StringBuilder sb = new StringBuilder();
        sb.append("会话状态摘要：\n");
        sb.append("- 项目: %s（主题: %s）\n".formatted(project.getTitle(), project.getTopic()));
        sb.append("- 阶段: %s\n".formatted(session.getCurrentStage()));
        sb.append("- 状态: %s\n".formatted(session.getStatus()));

        if (session.getOutlineJson() != null) {
            JsonNode outline = session.getOutlineJson();
            int pageCount = 0;
            if (outline.has("sections")) {
                for (JsonNode section : outline.path("sections")) {
                    pageCount += section.has("pages") ? section.path("pages").size() : 0;
                }
            }
            sb.append("- 大纲: 「%s」，%d 个章节，%d 页\n".formatted(
                outline.path("title").asText(""), outline.path("sections").size(), pageCount));
        } else {
            sb.append("- 大纲: 未生成\n");
        }

        if (session.getCurrentVersionId() != null) {
            List<WorkflowPageEntity> pages = pageRepository
                .findByWorkflowVersionIdOrderByOrderIndexAsc(session.getCurrentVersionId());
            sb.append("- 页面: %d 页".formatted(pages.size()));
            boolean hasFinal = pages.stream().anyMatch(p -> p.getFinalSvg() != null);
            sb.append(hasFinal ? "（含最终设计稿）\n" : "（草稿）\n");
            for (WorkflowPageEntity page : pages) {
                sb.append("  - P%d: %s [%s]\n".formatted(
                    page.getOrderIndex(), page.getTitle(), page.getId()));
            }
        } else {
            sb.append("- 页面: 未生成\n");
        }

        return sb.toString();
    }

    private JsonNode findPageResearch(String pageId, JsonNode pageResearch) {
        if (pageResearch == null || !pageResearch.isArray()) {
            return objectMapper.createObjectNode();
        }
        for (JsonNode item : pageResearch) {
            if (pageId.equals(item.path("pageId").asText())) {
                return item;
            }
        }
        return objectMapper.createObjectNode();
    }

    private WorkflowVersionEntity createVersion(
        UUID projectId, String templateId,
        JsonNode backgroundJson, JsonNode pageResearchJson, JsonNode outlineJson,
        WorkflowVersionSource source, String note
    ) {
        int nextVersionNumber = versionRepository.findTopByProjectIdOrderByVersionNumberDesc(projectId)
            .map(entity -> entity.getVersionNumber() + 1)
            .orElse(1);

        WorkflowVersionEntity version = new WorkflowVersionEntity();
        version.setId(UUID.randomUUID());
        version.setProjectId(projectId);
        version.setVersionNumber(nextVersionNumber);
        version.setSource(source);
        version.setNote(note);
        version.setTemplateId(templateId);
        version.setBackgroundJson(backgroundJson);
        version.setPageResearchJson(pageResearchJson);
        version.setOutlineJson(outlineJson);
        return versionRepository.save(version);
    }

    private void emitProgress(UUID sessionId, String toolName, String status, String description) {
        emitProgress(sessionId, toolName, status, description, null);
    }

    private void emitProgress(UUID sessionId, String toolName, String status,
                              String description, List<ToolSubStep> subSteps) {
        progressEmitter.emit(sessionId, new ToolProgressEvent(
            toolName, status, description, Instant.now(), subSteps
        ));
    }
}
