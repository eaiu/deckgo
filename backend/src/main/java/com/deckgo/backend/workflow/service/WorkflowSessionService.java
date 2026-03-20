package com.deckgo.backend.workflow.service;

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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowSessionService {

    private final WorkflowSessionRepository workflowSessionRepository;
    private final WorkflowMessageRepository workflowMessageRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowPageRepository workflowPageRepository;
    private final WorkflowContentService workflowContentService;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public WorkflowSessionService(
        WorkflowSessionRepository workflowSessionRepository,
        WorkflowMessageRepository workflowMessageRepository,
        WorkflowVersionRepository workflowVersionRepository,
        WorkflowPageRepository workflowPageRepository,
        WorkflowContentService workflowContentService,
        ProjectService projectService,
        ObjectMapper objectMapper
    ) {
        this.workflowSessionRepository = workflowSessionRepository;
        this.workflowMessageRepository = workflowMessageRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowPageRepository = workflowPageRepository;
        this.workflowContentService = workflowContentService;
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

        WorkflowSessionEntity session = new WorkflowSessionEntity();
        session.setId(UUID.randomUUID());
        session.setProjectId(project.getId());
        session.setStatus(WorkflowSessionStatus.WAITING_USER);
        session.setCurrentStage(WorkflowStage.DISCOVERY);
        session.setSelectedTemplateId(templateId);
        session.setDiscoveryJson(workflowContentService.generateDiscoveryCard(project));
        workflowSessionRepository.save(session);

        appendMessage(session.getId(), WorkflowMessageRole.USER, WorkflowStage.DISCOVERY, textContent(prompt));
        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.DISCOVERY, textContent("我先补齐几个关键问题，再继续推进这份 PPT。"));
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
        WorkflowSessionEntity session = findSession(sessionId);
        ProjectEntity project = projectService.findEntity(session.getProjectId());

        try {
            switch (request.command()) {
                case SUBMIT_DISCOVERY -> handleSubmitDiscovery(session, project, request);
                case CONTINUE_TO_OUTLINE -> handleContinueToOutline(session, project);
                case APPLY_OUTLINE_FEEDBACK -> handleApplyOutlineFeedback(session, request);
                case CONTINUE_TO_PAGE_PLAN -> handleContinueToPagePlan(session, project);
                case CONTINUE_TO_FINAL_DESIGN -> handleContinueToFinalDesign(session, project);
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

    private void handleSubmitDiscovery(WorkflowSessionEntity session, ProjectEntity project, WorkflowCommandRequest request) {
        ensureStage(session, WorkflowStage.DISCOVERY, WorkflowCommandType.SUBMIT_DISCOVERY);
        JsonNode answers = buildDiscoveryAnswers(request);
        appendMessage(session.getId(), WorkflowMessageRole.USER, WorkflowStage.DISCOVERY, answers);

        JsonNode research = workflowContentService.generateResearchSummary(project, answers);
        String audience = research.path("audience").asText(project.getAudience());
        String titleSuggestion = research.path("titleSuggestion").asText(project.getTitle());
        String templateId = research.path("suggestedTemplateId").asText(session.getSelectedTemplateId());

        project.setAudience(audience);
        project.setTitle(titleSuggestion);
        project.setTemplateId(templateId);

        session.setSelectedTemplateId(templateId);
        session.setResearchJson(research);
        session.setCurrentStage(WorkflowStage.RESEARCH);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.RESEARCH, textContent("我已经整理出当前阶段的资料摘要，你可以先看一眼，再继续生成大纲。"));
    }

    private void handleContinueToOutline(WorkflowSessionEntity session, ProjectEntity project) {
        ensureStage(session, WorkflowStage.RESEARCH, WorkflowCommandType.CONTINUE_TO_OUTLINE);
        JsonNode outline = workflowContentService.generateOutline(project, session.getResearchJson());
        session.setOutlineJson(outline);
        session.setCurrentStage(WorkflowStage.OUTLINE);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);

        if (outline.hasNonNull("title")) {
            project.setTitle(outline.path("title").asText(project.getTitle()));
        }

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.OUTLINE, textContent("大纲已经生成好了。你可以查看详情、提出修改意见，然后再继续生成页面策划稿。"));
    }

    private void handleApplyOutlineFeedback(WorkflowSessionEntity session, WorkflowCommandRequest request) {
        ensureStage(session, WorkflowStage.OUTLINE, WorkflowCommandType.APPLY_OUTLINE_FEEDBACK);
        String feedback = request.feedback() == null ? "" : request.feedback().trim();
        if (feedback.isBlank()) {
            throw new ValidationException("outline feedback 不能为空", List.of("feedback 不能为空"));
        }

        appendMessage(session.getId(), WorkflowMessageRole.USER, WorkflowStage.OUTLINE, textContent(feedback));
        JsonNode revisedOutline = workflowContentService.reviseOutline(
            projectService.findEntity(session.getProjectId()),
            session.getOutlineJson(),
            feedback
        );
        session.setOutlineJson(revisedOutline);
        session.setCurrentVersionId(null);

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.OUTLINE, textContent("我已经根据你的要求更新了大纲。确认后就可以继续生成页面策划稿。"));
    }

    private void handleContinueToPagePlan(WorkflowSessionEntity session, ProjectEntity project) {
        ensureStage(session, WorkflowStage.OUTLINE, WorkflowCommandType.CONTINUE_TO_PAGE_PLAN);
        List<JsonNode> pagePlans = workflowContentService.generatePagePlans(project, session.getOutlineJson());

        WorkflowVersionEntity draftVersion = createVersion(
            project.getId(),
            session.getSelectedTemplateId(),
            session.getResearchJson(),
            session.getOutlineJson(),
            WorkflowVersionSource.DRAFT,
            "自动生成页面策划稿"
        );

        int index = 1;
        for (JsonNode pagePlan : pagePlans) {
            WorkflowPageEntity page = new WorkflowPageEntity();
            page.setId(UUID.randomUUID());
            page.setWorkflowVersionId(draftVersion.getId());
            page.setOrderIndex(index);
            page.setTitle(pagePlan.path("title").asText("未命名页面"));
            page.setPagePlanJson(pagePlan);
            page.setDraftSvg(workflowContentService.renderDraftSvg(pagePlan, session.getSelectedTemplateId()));
            workflowPageRepository.save(page);
            index++;
        }

        session.setCurrentVersionId(draftVersion.getId());
        session.setCurrentStage(WorkflowStage.DRAFT);
        session.setStatus(WorkflowSessionStatus.WAITING_USER);

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.DRAFT, textContent("页面策划稿和低保真草稿已经生成，你可以先预览整体结构，再继续生成最终设计稿。"));
    }

    private void handleContinueToFinalDesign(WorkflowSessionEntity session, ProjectEntity project) {
        ensureStage(session, WorkflowStage.DRAFT, WorkflowCommandType.CONTINUE_TO_FINAL_DESIGN);
        if (session.getCurrentVersionId() == null) {
            throw new NotFoundException("当前会话还没有可用的草稿版本");
        }

        List<WorkflowPageEntity> draftPages = workflowPageRepository.findByWorkflowVersionIdOrderByOrderIndexAsc(session.getCurrentVersionId());
        if (draftPages.isEmpty()) {
            throw new NotFoundException("当前草稿版本没有页面数据");
        }

        WorkflowVersionEntity finalVersion = createVersion(
            project.getId(),
            session.getSelectedTemplateId(),
            session.getResearchJson(),
            session.getOutlineJson(),
            WorkflowVersionSource.FINAL,
            "生成最终 SVG 设计稿"
        );

        for (WorkflowPageEntity draftPage : draftPages) {
            WorkflowPageEntity page = new WorkflowPageEntity();
            page.setId(UUID.randomUUID());
            page.setWorkflowVersionId(finalVersion.getId());
            page.setOrderIndex(draftPage.getOrderIndex());
            page.setTitle(draftPage.getTitle());
            page.setPagePlanJson(draftPage.getPagePlanJson());
            page.setDraftSvg(draftPage.getDraftSvg());
            page.setFinalSvg(workflowContentService.renderFinalSvg(draftPage.getPagePlanJson(), session.getSelectedTemplateId()));
            workflowPageRepository.save(page);
        }

        session.setCurrentVersionId(finalVersion.getId());
        session.setCurrentStage(WorkflowStage.FINAL);
        session.setStatus(WorkflowSessionStatus.COMPLETED);

        appendMessage(session.getId(), WorkflowMessageRole.ASSISTANT, WorkflowStage.FINAL, textContent("最终 SVG 页面集已经生成完成，现在可以逐页浏览这份演示稿。"));
    }

    private WorkflowVersionEntity createVersion(
        UUID projectId,
        String templateId,
        JsonNode researchJson,
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
        version.setResearchJson(researchJson);
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
            session.getDiscoveryJson(),
            session.getResearchJson(),
            session.getOutlineJson(),
            pages,
            session.getUpdatedAt()
        );
    }
}
