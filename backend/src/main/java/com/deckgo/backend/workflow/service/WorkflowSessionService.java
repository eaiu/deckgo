package com.deckgo.backend.workflow.service;

import com.deckgo.backend.ai.service.DiscoveryAgentService;
import com.deckgo.backend.ai.service.OutlineAgentService;
import com.deckgo.backend.ai.service.PagePlanAgentService;
import com.deckgo.backend.ai.service.ResearchAgentService;
import com.deckgo.backend.ai.service.SvgDesignAgentService;
import com.deckgo.backend.ai.service.TavilySearchService;
import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.common.exception.ValidationException;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.workflow.dto.CreateWorkflowSessionRequest;
import com.deckgo.backend.workflow.dto.WorkflowCommandRequest;
import com.deckgo.backend.workflow.dto.WorkflowMessageResponse;
import com.deckgo.backend.workflow.dto.WorkflowPageResponse;
import com.deckgo.backend.workflow.dto.WorkflowProjectResponse;
import com.deckgo.backend.workflow.dto.WorkflowSessionResponse;
import com.deckgo.backend.workflow.entity.WorkflowMessageEntity;
import com.deckgo.backend.workflow.entity.WorkflowPageEntity;
import com.deckgo.backend.workflow.entity.WorkflowSessionEntity;
import com.deckgo.backend.workflow.entity.WorkflowVersionEntity;
import com.deckgo.backend.workflow.enums.WorkflowCommandType;
import com.deckgo.backend.workflow.enums.WorkflowMessageRole;
import com.deckgo.backend.workflow.enums.WorkflowSessionStatus;
import com.deckgo.backend.workflow.enums.WorkflowStage;
import com.deckgo.backend.workflow.enums.WorkflowVersionSource;
import com.deckgo.backend.workflow.repository.WorkflowMessageRepository;
import com.deckgo.backend.workflow.repository.WorkflowPageRepository;
import com.deckgo.backend.workflow.repository.WorkflowSessionRepository;
import com.deckgo.backend.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowSessionService {

    private final WorkflowSessionRepository workflowSessionRepository;
    private final WorkflowMessageRepository workflowMessageRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowPageRepository workflowPageRepository;
    private final WorkflowContentService workflowContentService;
    private final TavilySearchService tavilySearchService;
    private final DiscoveryAgentService discoveryAgentService;
    private final ResearchAgentService researchAgentService;
    private final OutlineAgentService outlineAgentService;
    private final PagePlanAgentService pagePlanAgentService;
    private final SvgDesignAgentService svgDesignAgentService;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public WorkflowSessionService(
        WorkflowSessionRepository workflowSessionRepository,
        WorkflowMessageRepository workflowMessageRepository,
        WorkflowVersionRepository workflowVersionRepository,
        WorkflowPageRepository workflowPageRepository,
        WorkflowContentService workflowContentService,
        TavilySearchService tavilySearchService,
        DiscoveryAgentService discoveryAgentService,
        ResearchAgentService researchAgentService,
        OutlineAgentService outlineAgentService,
        PagePlanAgentService pagePlanAgentService,
        SvgDesignAgentService svgDesignAgentService,
        ProjectService projectService,
        ObjectMapper objectMapper
    ) {
        this.workflowSessionRepository = workflowSessionRepository;
        this.workflowMessageRepository = workflowMessageRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowPageRepository = workflowPageRepository;
        this.workflowContentService = workflowContentService;
        this.tavilySearchService = tavilySearchService;
        this.discoveryAgentService = discoveryAgentService;
        this.researchAgentService = researchAgentService;
        this.outlineAgentService = outlineAgentService;
        this.pagePlanAgentService = pagePlanAgentService;
        this.svgDesignAgentService = svgDesignAgentService;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowSessionResponse createSession(CreateWorkflowSessionRequest request) {
        String prompt = request.prompt().trim();
        String templateId = workflowContentService.deriveTemplateId(prompt);
        ProjectEntity project = projectService.createWorkflowProject(
            workflowContentService.deriveProjectTitle(prompt),
            prompt,
            "待确认",
            templateId
        );

        JsonNode backgroundSummary = tavilySearchService.collectBackgroundSummary(prompt)
            .orElseGet(() -> workflowContentService.generateBackgroundSummary(project));

        WorkflowSessionEntity session = new WorkflowSessionEntity();
        session.setId(UUID.randomUUID());
        session.setProjectId(project.getId());
        session.setStatus(WorkflowSessionStatus.WAITING_USER);
        session.setCurrentStage(WorkflowStage.DISCOVERY);
        session.setSelectedTemplateId(templateId);
        session.setBackgroundJson(backgroundSummary);
        session.setDiscoveryJson(discoveryAgentService.generateDiscoveryCard(project, backgroundSummary));
        workflowSessionRepository.save(session);

        appendMessage(session.getId(), WorkflowMessageRole.USER, WorkflowStage.DISCOVERY, textContent(prompt));
        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.DISCOVERY, textContent("我先查了一下背景信息，并基于它生成了几个需要你确认的问题。"));
        return toResponse(session, project);
    }

    @Transactional(readOnly = true)
    public WorkflowSessionResponse getSession(UUID sessionId) {
        WorkflowSessionEntity session = findSession(sessionId);
        ProjectEntity project = projectService.findEntity(session.getProjectId());
        return toResponse(session, project);
    }

    @Transactional
    public WorkflowSessionResponse executeCommand(UUID sessionId, WorkflowCommandRequest request) {
        WorkflowSessionEntity session = findSessionForUpdate(sessionId);
        ProjectEntity project = projectService.findEntityForUpdate(session.getProjectId());

        try {
            switch (request.command()) {
                case SUBMIT_DISCOVERY -> handleSubmitDiscovery(session, project, request);
                case APPLY_OUTLINE_FEEDBACK -> handleApplyOutlineFeedback(session, project, request);
                case CONTINUE_TO_RESEARCH -> handleContinueToResearch(session, project);
                case CONTINUE_TO_PLANNING -> handleContinueToPlanning(session, project);
                case CONTINUE_TO_DESIGN -> handleContinueToDesign(session, project);
                default -> throw new ValidationException("不支持的 command", List.of(request.command().name()));
            }
            session.setLastError(null);
            workflowSessionRepository.save(session);
            projectService.saveEntity(project);
            return toResponse(session, project);
        } catch (RuntimeException exception) {
            session.setStatus(WorkflowSessionStatus.FAILED);
            session.setLastError(exception.getMessage());
            workflowSessionRepository.save(session);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public WorkflowSessionEntity findSession(UUID sessionId) {
        return workflowSessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("工作流会话不存在: " + sessionId));
    }

    @Transactional
    public WorkflowSessionEntity findSessionForUpdate(UUID sessionId) {
        WorkflowSessionEntity session = workflowSessionRepository.findByIdForUpdate(sessionId);
        if (session == null) {
            throw new NotFoundException("工作流会话不存在: " + sessionId);
        }
        return session;
    }

    private void handleSubmitDiscovery(WorkflowSessionEntity session, ProjectEntity project, WorkflowCommandRequest request) {
        ensureStage(session, WorkflowStage.DISCOVERY, WorkflowCommandType.SUBMIT_DISCOVERY);
        JsonNode answers = buildDiscoveryAnswers(request);
        appendMessage(session.getId(), WorkflowMessageRole.USER, WorkflowStage.DISCOVERY, answers);

        session.setDiscoveryAnswersJson(answers);
        JsonNode outline = outlineAgentService.generateOutline(project, session.getBackgroundJson(), answers);
        session.setOutlineJson(outline);
        session.setPageResearchJson(null);
        session.setCurrentVersionId(null);
        session.setCurrentStage(WorkflowStage.OUTLINE);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);

        if (outline.hasNonNull("title")) {
            project.setTitle(outline.path("title").asText(project.getTitle()));
        }

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.OUTLINE, textContent("大纲已经生成好了。你可以先检查结构，再继续进入按页资料搜集阶段。"));
    }

    private void handleApplyOutlineFeedback(WorkflowSessionEntity session, ProjectEntity project, WorkflowCommandRequest request) {
        ensureStage(session, WorkflowStage.OUTLINE, WorkflowCommandType.APPLY_OUTLINE_FEEDBACK);
        String feedback = request.feedback() == null ? "" : request.feedback().trim();
        if (feedback.isBlank()) {
            throw new ValidationException("outline feedback 不能为空", List.of("feedback 不能为空"));
        }

        appendMessage(session.getId(), WorkflowMessageRole.USER, WorkflowStage.OUTLINE, textContent(feedback));
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

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.OUTLINE, textContent("我已经根据你的要求更新了大纲。确认后就可以继续进行逐页资料搜集。"));
    }

    private void handleContinueToResearch(WorkflowSessionEntity session, ProjectEntity project) {
        ensureStage(session, WorkflowStage.OUTLINE, WorkflowCommandType.CONTINUE_TO_RESEARCH);
        JsonNode pageResearch = researchAgentService.generatePageResearch(
            project,
            session.getBackgroundJson(),
            session.getDiscoveryAnswersJson(),
            session.getOutlineJson()
        );

        session.setPageResearchJson(pageResearch);
        session.setCurrentStage(WorkflowStage.RESEARCH);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.RESEARCH, textContent("逐页资料搜集已经完成。现在每一页都绑定了研究结果，可以继续进入策划阶段。"));
    }

    private void handleContinueToPlanning(WorkflowSessionEntity session, ProjectEntity project) {
        ensureStage(session, WorkflowStage.RESEARCH, WorkflowCommandType.CONTINUE_TO_PLANNING);
        List<JsonNode> pagePlans = pagePlanAgentService.generatePagePlans(
            project,
            session.getBackgroundJson(),
            session.getDiscoveryAnswersJson(),
            session.getOutlineJson(),
            session.getPageResearchJson(),
            session.getSelectedTemplateId()
        );

        WorkflowVersionEntity planningVersion = createVersion(
            project.getId(),
            session.getSelectedTemplateId(),
            session.getBackgroundJson(),
            session.getPageResearchJson(),
            session.getOutlineJson(),
            WorkflowVersionSource.PLANNING,
            "自动生成页面策划稿"
        );

        int index = 1;
        List<CompletableFuture<String>> draftFutures = pagePlans.stream()
            .map(plan -> CompletableFuture.supplyAsync(() ->
                svgDesignAgentService.generateDraftSvg(
                    plan,
                    findPageResearch(plan.path("pageId").asText(""), session.getPageResearchJson()),
                    session.getSelectedTemplateId()
                )
            ))
            .toList();

        for (int i = 0; i < pagePlans.size(); i++) {
            JsonNode pagePlan = pagePlans.get(i);
            WorkflowPageEntity page = new WorkflowPageEntity();
            page.setId(UUID.randomUUID());
            page.setWorkflowVersionId(planningVersion.getId());
            page.setOrderIndex(index);
            page.setTitle(pagePlan.path("title").asText("未命名页面"));
            page.setPagePlanJson(pagePlan);
            page.setDraftSvg(draftFutures.get(i).join());
            workflowPageRepository.save(page);
            index++;
        }

        session.setCurrentVersionId(planningVersion.getId());
        session.setCurrentStage(WorkflowStage.PLANNING);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.PLANNING, textContent("页面策划已经生成，当前每一页都包含布局框架和素材类型需求。"));
    }

    private void handleContinueToDesign(WorkflowSessionEntity session, ProjectEntity project) {
        ensureStage(session, WorkflowStage.PLANNING, WorkflowCommandType.CONTINUE_TO_DESIGN);
        if (session.getCurrentVersionId() == null) {
            throw new NotFoundException("当前会话还没有可用的策划版本");
        }

        List<WorkflowPageEntity> planningPages = workflowPageRepository.findByWorkflowVersionIdOrderByOrderIndexAsc(session.getCurrentVersionId());
        if (planningPages.isEmpty()) {
            throw new NotFoundException("当前策划版本没有页面数据");
        }

        WorkflowVersionEntity designVersion = createVersion(
            project.getId(),
            session.getSelectedTemplateId(),
            session.getBackgroundJson(),
            session.getPageResearchJson(),
            session.getOutlineJson(),
            WorkflowVersionSource.DESIGN,
            "生成最终 SVG 设计稿"
        );

        List<CompletableFuture<String>> finalFutures = planningPages.stream()
            .map(planningPage -> CompletableFuture.supplyAsync(() ->
                svgDesignAgentService.generateFinalSvg(
                    planningPage.getPagePlanJson(),
                    findPageResearch(planningPage.getPagePlanJson().path("pageId").asText(""), session.getPageResearchJson()),
                    session.getSelectedTemplateId()
                )
            ))
            .toList();

        for (int i = 0; i < planningPages.size(); i++) {
            WorkflowPageEntity planningPage = planningPages.get(i);
            WorkflowPageEntity page = new WorkflowPageEntity();
            page.setId(UUID.randomUUID());
            page.setWorkflowVersionId(designVersion.getId());
            page.setOrderIndex(planningPage.getOrderIndex());
            page.setTitle(planningPage.getTitle());
            page.setPagePlanJson(planningPage.getPagePlanJson());
            page.setDraftSvg(planningPage.getDraftSvg());
            page.setFinalSvg(finalFutures.get(i).join());
            workflowPageRepository.save(page);
        }

        session.setCurrentVersionId(designVersion.getId());
        session.setCurrentStage(WorkflowStage.DESIGN);
        session.setStatus(WorkflowSessionStatus.COMPLETED);

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.DESIGN, textContent("最终 SVG 页面集已经生成完成，现在可以逐页查看最终设计稿。"));
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
        UUID projectId,
        String templateId,
        JsonNode backgroundJson,
        JsonNode pageResearchJson,
        JsonNode outlineJson,
        WorkflowVersionSource source,
        String note
    ) {
        int nextVersionNumber = workflowVersionRepository.findTopByProjectIdOrderByVersionNumberDesc(projectId)
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
        return workflowVersionRepository.save(version);
    }

    private JsonNode buildDiscoveryAnswers(WorkflowCommandRequest request) {
        ObjectNode answers = objectMapper.createObjectNode();
        ArrayNode selected = answers.putArray("selectedOptionIds");
        List<String> ids = request.selectedOptionIds() == null ? List.of() : request.selectedOptionIds();
        ids.forEach(selected::add);
        answers.put("freeformAnswer", request.freeformAnswer() == null ? "" : request.freeformAnswer().trim());
        return answers;
    }

    private void appendMessage(UUID sessionId, WorkflowMessageRole role, WorkflowStage stage, JsonNode content) {
        WorkflowMessageEntity message = new WorkflowMessageEntity();
        message.setId(UUID.randomUUID());
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setStage(stage);
        message.setContentJson(content);
        workflowMessageRepository.save(message);
    }

    private JsonNode textContent(String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("text", text);
        return node;
    }

    private void ensureStage(WorkflowSessionEntity session, WorkflowStage expectedStage, WorkflowCommandType command) {
        if (session.getCurrentStage() != expectedStage) {
            throw new ValidationException(
                "当前阶段不允许执行该命令",
                List.of("command=%s expectedStage=%s actualStage=%s".formatted(command, expectedStage, session.getCurrentStage()))
            );
        }
        if (session.getStatus() == WorkflowSessionStatus.FAILED) {
            throw new ValidationException("当前会话处于失败状态，请重新创建会话", List.of(session.getLastError()));
        }
    }

    private WorkflowSessionResponse toResponse(WorkflowSessionEntity session, ProjectEntity project) {
        List<WorkflowMessageResponse> messages = workflowMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
            .map(message -> new WorkflowMessageResponse(
                message.getId(),
                message.getRole(),
                message.getStage(),
                message.getContentJson(),
                message.getCreatedAt()
            ))
            .toList();

        List<WorkflowPageResponse> pages = session.getCurrentVersionId() == null
            ? List.of()
            : workflowPageRepository.findByWorkflowVersionIdOrderByOrderIndexAsc(session.getCurrentVersionId()).stream()
                .map(page -> new WorkflowPageResponse(
                    page.getId(),
                    page.getOrderIndex(),
                    page.getTitle(),
                    page.getPagePlanJson(),
                    page.getDraftSvg(),
                    page.getFinalSvg()
                ))
                .toList();

        return new WorkflowSessionResponse(
            session.getId(),
            session.getCurrentStage(),
            session.getStatus(),
            session.getCurrentVersionId(),
            session.getSelectedTemplateId(),
            session.getLastError(),
            new WorkflowProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getTopic(),
                project.getAudience(),
                project.getTemplateId()
            ),
            messages,
            session.getBackgroundJson(),
            session.getDiscoveryJson(),
            session.getDiscoveryAnswersJson(),
            session.getOutlineJson(),
            session.getPageResearchJson(),
            pages,
            session.getUpdatedAt()
        );
    }
}
