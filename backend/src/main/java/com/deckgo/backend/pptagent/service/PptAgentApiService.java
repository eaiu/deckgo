package com.deckgo.backend.pptagent.service;

import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.pptagent.dto.PptActionJobResponse;
import com.deckgo.backend.pptagent.dto.PptCitationResponse;
import com.deckgo.backend.pptagent.dto.PptConfirmRequest;
import com.deckgo.backend.pptagent.dto.PptCorpusDigestResponse;
import com.deckgo.backend.pptagent.dto.PptDesignVersionResponse;
import com.deckgo.backend.pptagent.dto.PptDraftVersionResponse;
import com.deckgo.backend.pptagent.dto.PptExportJobResponse;
import com.deckgo.backend.pptagent.dto.PptBackgroundUploadResponse;
import com.deckgo.backend.pptagent.dto.PptExportCreateRequest;
import com.deckgo.backend.pptagent.dto.PptMessageCreateRequest;
import com.deckgo.backend.pptagent.dto.PptPageResponse;
import com.deckgo.backend.pptagent.dto.PptPageSearchQueryResponse;
import com.deckgo.backend.pptagent.dto.PptPageSearchResultResponse;
import com.deckgo.backend.pptagent.dto.PptProjectCreateRequest;
import com.deckgo.backend.pptagent.dto.PptProjectSummaryResponse;
import com.deckgo.backend.pptagent.dto.PptRequirementAnswerRequest;
import com.deckgo.backend.pptagent.dto.PptRequirementFormResponse;
import com.deckgo.backend.pptagent.dto.PptRequirementQuestionCreateRequest;
import com.deckgo.backend.pptagent.dto.PptRequirementQuestionPatchRequest;
import com.deckgo.backend.pptagent.dto.PptRequirementQuestionOptionResponse;
import com.deckgo.backend.pptagent.dto.PptRequirementQuestionResponse;
import com.deckgo.backend.pptagent.dto.PptRequirementSourceResponse;
import com.deckgo.backend.project.dto.ProjectCreateRequest;
import com.deckgo.backend.project.dto.ProjectDetailResponse;
import com.deckgo.backend.project.dto.ProjectResponse;
import com.deckgo.backend.project.dto.RequirementAnswerPatchRequest;
import com.deckgo.backend.project.dto.RequirementAnswerItemRequest;
import com.deckgo.backend.project.dto.RequirementAnswersBatchRequest;
import com.deckgo.backend.project.dto.RequirementConfirmRequest;
import com.deckgo.backend.project.service.ProjectRequirementService;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.studio.dto.OutlineVersionSnapshot;
import com.deckgo.backend.studio.dto.ProjectMessageSnapshot;
import com.deckgo.backend.studio.dto.ProjectPageSnapshot;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.dto.RequirementFormSnapshot;
import com.deckgo.backend.studio.service.ProjectStudioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PptAgentApiService {

    private final ProjectService projectService;
    private final ProjectRequirementService projectRequirementService;
    private final ProjectStudioService projectStudioService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PptAgentApiService(
        ProjectService projectService,
        ProjectRequirementService projectRequirementService,
        ProjectStudioService projectStudioService,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.projectRequirementService = projectRequirementService;
        this.projectStudioService = projectStudioService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PptProjectSummaryResponse> listProjects() {
        return projectService.listProjects().stream().map(this::mapProjectSummary).toList();
    }

    public PptProjectSummaryResponse createProject(PptProjectCreateRequest request) {
        ProjectDetailResponse created = projectService.createProject(new ProjectCreateRequest(
            deriveTitle(request.title(), request.requestText()),
            request.requestText().trim()
        ));
        return mapProjectSummary(created);
    }

    public PptProjectSummaryResponse getProject(UUID projectId) {
        return mapProjectSummary(projectService.getProject(projectId));
    }

    public List<ProjectMessageSnapshot> listMessages(UUID projectId) {
        return projectStudioService.getProject(projectId).messages();
    }

    public List<com.deckgo.backend.studio.dto.StageRunSnapshot> listProjectRuns(UUID projectId) {
        return projectStudioService.getProject(projectId).projectRuns();
    }

    public ProjectMessageSnapshot createMessage(UUID projectId, PptMessageCreateRequest request) {
        ProjectStudioSnapshot snapshot = projectStudioService.getProject(projectId);
        String currentStage = snapshot.currentStage();
        String scopeType = request.scopeType() == null || request.scopeType().isBlank()
            ? (request.targetPageId() == null ? "PROJECT" : "PAGE")
            : request.scopeType().trim().toUpperCase();
        UUID targetPageId = request.targetPageId();
        UUID messageId = UUID.randomUUID();
        OffsetDateTime createdAt = utcNow();

        appendProjectMessage(
            messageId,
            projectId,
            currentStage,
            scopeType,
            targetPageId,
            "USER",
            request.contentMd().trim(),
            buildUserPayload(request)
        );

        UUID agentRunId = UUID.randomUUID();
        RouterDecision decision = routeMessage(projectId, currentStage, scopeType, targetPageId, request.contentMd().trim());
        List<ObjectNode> stepResults = new ArrayList<>();

        appendProjectEvent(projectId, "agent.run.started", currentStage, scopeType, targetPageId, agentRunId, objectNode("title", "处理聊天动作", "origin", "message", "message_id", messageId.toString()));
        appendProjectEvent(projectId, "router.decision", currentStage, scopeType, targetPageId, agentRunId, decision.payload());
        appendProjectEvent(projectId, "recommendations.updated", currentStage, scopeType, targetPageId, agentRunId, objectNode("next_recommendations", decision.recommendations()));

        if ("reject".equals(decision.actionType())) {
            UUID assistantMessageId = UUID.randomUUID();
            appendProjectMessage(
                assistantMessageId,
                projectId,
                currentStage,
                scopeType,
                targetPageId,
                "ASSISTANT",
                decision.reason(),
                buildAgentRunPayload(agentRunId, "处理聊天动作", decision.payload(), stepResults, decision.recommendations(), objectNode("status", "rejected"))
            );
            appendProjectEvent(projectId, "agent.message", currentStage, scopeType, targetPageId, agentRunId, objectNode("message_id", assistantMessageId.toString()));
            appendProjectEvent(projectId, "agent.run.completed", currentStage, scopeType, targetPageId, agentRunId, objectNode("status", "rejected"));
        } else {
            ObjectNode startedStep = stepPayload(decision.actionType(), decision.stepName(), "running", decision.reason(), null, null, null);
            stepResults.add(startedStep.deepCopy());
            appendProjectEvent(projectId, "action.step.started", currentStage, scopeType, targetPageId, agentRunId, startedStep);

            try {
                ObjectNode resultSnapshot = executeRoutedAction(projectId, targetPageId, decision, request.contentMd().trim());
                ObjectNode completedStep = stepPayload(decision.actionType(), decision.stepName(), "completed", decision.reason(), resultSnapshot, null, null);
                stepResults.set(stepResults.size() - 1, completedStep.deepCopy());
                appendProjectEvent(projectId, "action.step.completed", currentStage, scopeType, targetPageId, agentRunId, completedStep);

                UUID assistantMessageId = UUID.randomUUID();
                appendProjectMessage(
                    assistantMessageId,
                    projectId,
                    currentStage,
                    scopeType,
                    targetPageId,
                    "ASSISTANT",
                    decision.successMessage(),
                    buildAgentRunPayload(agentRunId, "处理聊天动作", decision.payload(), stepResults, decision.recommendations(), resultSnapshot)
                );
                appendProjectEvent(projectId, "agent.message", currentStage, scopeType, targetPageId, agentRunId, objectNode("message_id", assistantMessageId.toString()));
                appendProjectEvent(projectId, "agent.run.completed", currentStage, scopeType, targetPageId, agentRunId, objectNode("status", "completed"));
            } catch (RuntimeException exception) {
                ObjectNode failedStep = stepPayload(decision.actionType(), decision.stepName(), "failed", decision.reason(), null, exception.getMessage(), null);
                stepResults.set(stepResults.size() - 1, failedStep.deepCopy());
                appendProjectEvent(projectId, "action.step.failed", currentStage, scopeType, targetPageId, agentRunId, failedStep);

                UUID assistantMessageId = UUID.randomUUID();
                appendProjectMessage(
                    assistantMessageId,
                    projectId,
                    currentStage,
                    scopeType,
                    targetPageId,
                    "ASSISTANT",
                    decision.stepName() + "失败：" + exception.getMessage(),
                    buildAgentRunPayload(agentRunId, "处理聊天动作", decision.payload(), stepResults, decision.recommendations(), objectNode("error_message", exception.getMessage()))
                );
                appendProjectEvent(projectId, "agent.message", currentStage, scopeType, targetPageId, agentRunId, objectNode("message_id", assistantMessageId.toString()));
                appendProjectEvent(projectId, "agent.run.completed", currentStage, scopeType, targetPageId, agentRunId, objectNode("status", "failed"));
                throw exception;
            }
        }

        return new ProjectMessageSnapshot(
            messageId,
            currentStage,
            scopeType,
            targetPageId,
            "USER",
            request.contentMd().trim(),
            buildUserPayload(request),
            createdAt
        );
    }

    public PptRequirementFormResponse getRequirementForm(UUID projectId) {
        return mapRequirementForm(projectId, projectRequirementService.getRequirementForm(projectId));
    }

    public PptRequirementFormResponse submitRequirementAnswers(UUID projectId, List<PptRequirementAnswerRequest> answers) {
        RequirementAnswersBatchRequest batch = new RequirementAnswersBatchRequest(
            answers.stream()
                .map(item -> new RequirementAnswerItemRequest(item.questionCode(), item.value()))
                .toList()
        );
        return mapRequirementForm(projectId, projectRequirementService.submitRequirementAnswers(projectId, batch));
    }

    public PptRequirementFormResponse patchRequirementAnswer(UUID projectId, String questionCode, JsonNode value) {
        return mapRequirementForm(projectId, projectRequirementService.patchRequirementAnswer(projectId, questionCode, new RequirementAnswerPatchRequest(value)));
    }

    public PptRequirementFormResponse retryRequirementSource(UUID projectId, String sourceId) {
        RequirementFormSnapshot snapshot = projectRequirementService.getRequirementForm(projectId);
        JsonNode initSearch = snapshot.initSearchResults() == null ? emptyObject() : snapshot.initSearchResults().deepCopy();
        ArrayNode sources = initSearch.withArray("sources");
        for (JsonNode item : sources) {
            if (sourceId.equals(item.path("id").asText(""))) {
                ((ObjectNode) item).put("content", item.path("content").asText(item.path("snippet").asText("")) + "（已重试刷新）");
            }
        }
        jdbcTemplate.update(
            "update requirement_forms set init_search_results_json = cast(? as jsonb), updated_at = ? where project_id = ?",
            jsonText(initSearch),
            Timestamp.from(utcNow().toInstant()),
            projectId
        );
        return getRequirementForm(projectId);
    }

    public PptRequirementFormResponse createRequirementQuestion(UUID projectId, PptRequirementQuestionCreateRequest request) {
        return upsertRequirementQuestion(projectId, request.questionCode(), request.label(), request.description(), request.options(), request.allowCustom());
    }

    public PptRequirementFormResponse patchRequirementQuestion(UUID projectId, String questionCode, PptRequirementQuestionPatchRequest request) {
        RequirementFormSnapshot snapshot = projectRequirementService.getRequirementForm(projectId);
        ObjectNode aiQuestions = snapshot.aiQuestions() instanceof ObjectNode objectNode ? objectNode.deepCopy() : objectMapper.createObjectNode();
        ArrayNode questions = aiQuestions.withArray("questions");
        ObjectNode target = null;
        for (JsonNode node : questions) {
            if (questionCode.equals(node.path("id").asText(node.path("questionCode").asText(""))) && node instanceof ObjectNode objectNode) {
                target = objectNode;
                break;
            }
        }
        if (target == null) {
            throw new NotFoundException("问题不存在: " + questionCode);
        }
        if (request.label() != null) target.put("label", request.label());
        if (request.label() != null) target.put("prompt", request.label());
        if (request.description() != null) target.put("description", request.description());
        if (request.allowCustom() != null) target.put("allowCustom", request.allowCustom());
        if (request.options() != null) {
            ArrayNode options = objectMapper.createArrayNode();
            request.options().forEach(option -> options.add(objectNode(
                "id", option.optionCode(),
                "optionCode", option.optionCode(),
                "label", option.label(),
                "description", option.description()
            )));
            target.set("options", options);
        }
        persistAiQuestions(projectId, aiQuestions);
        return getRequirementForm(projectId);
    }

    public PptRequirementFormResponse deleteRequirementQuestion(UUID projectId, String questionCode) {
        RequirementFormSnapshot snapshot = projectRequirementService.getRequirementForm(projectId);
        ObjectNode aiQuestions = snapshot.aiQuestions() instanceof ObjectNode objectNode ? objectNode.deepCopy() : objectMapper.createObjectNode();
        ArrayNode questions = objectMapper.createArrayNode();
        for (JsonNode node : iterable(aiQuestions.path("questions"))) {
            String code = node.path("id").asText(node.path("questionCode").asText(""));
            if (!questionCode.equals(code)) {
                questions.add(node);
            }
        }
        aiQuestions.set("questions", questions);
        persistAiQuestions(projectId, aiQuestions);
        return getRequirementForm(projectId);
    }

    public ProjectStudioSnapshot confirmRequirements(UUID projectId, PptConfirmRequest request) {
        return projectRequirementService.confirmRequirements(projectId, new RequirementConfirmRequest(request == null ? null : request.noteMd()));
    }

    public OutlineVersionSnapshot getOutline(UUID projectId) {
        ProjectStudioSnapshot snapshot = projectStudioService.getProject(projectId);
        if (snapshot.currentOutline() == null) {
            throw new NotFoundException("当前项目还没有 outline");
        }
        UUID outlineId = snapshot.currentOutlineVersionId();
        return jdbcTemplate.query(
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
        ).stream().findFirst().orElseThrow(() -> new NotFoundException("当前项目还没有 outline"));
    }

    public OutlineVersionSnapshot patchStoryboard(UUID projectId, JsonNode partsPayload) {
        return projectStudioService.patchStoryboard(projectId, partsPayload);
    }

    public List<PptPageResponse> listPages(UUID projectId) {
        return projectStudioService.getProject(projectId).pages().stream()
            .map(page -> mapPage(projectId, page))
            .toList();
    }

    public PptPageResponse getPage(UUID projectId, UUID pageId) {
        return mapPage(projectId, projectStudioService.getPage(projectId, pageId));
    }

    public PptActionJobResponse runPageAction(UUID projectId, UUID pageId, String actionType, boolean replaceExisting) {
        switch (actionType) {
            case "page_generate_search_queries" -> projectStudioService.preparePageResearch(projectId, pageId);
            case "page_search_run", "page_search_refresh" -> projectStudioService.runPageResearch(projectId, pageId, replaceExisting);
            case "page_summary_generate" -> {
                ProjectPageSnapshot page = projectStudioService.getPage(projectId, pageId);
                if (page.currentResearchSessionId() == null) {
                    page = projectStudioService.runPageResearch(projectId, pageId, true);
                }
                String summary = page.currentResearch() != null ? page.currentResearch().path("findings").asText("") : "";
                projectStudioService.patchPageSummary(projectId, pageId, summary);
            }
            case "page_draft_generate" -> projectStudioService.generatePagePlanning(projectId, pageId);
            case "page_design_generate" -> projectStudioService.generatePageDesign(projectId, pageId);
            default -> throw new IllegalArgumentException("不支持的页面动作: " + actionType);
        }
        return new PptActionJobResponse("COMPLETED", UUID.randomUUID());
    }

    public PptPageResponse patchPageSummary(UUID projectId, UUID pageId, String summaryMd) {
        return mapPage(projectId, projectStudioService.patchPageSummary(projectId, pageId, summaryMd));
    }

    public PptPageResponse redesignPage(UUID projectId, UUID pageId, String instruction) {
        return mapPage(projectId, projectStudioService.redesignPage(projectId, pageId, instruction));
    }

    public PptPageResponse retryPageSearchResult(UUID projectId, UUID pageId, String sourceId) {
        ProjectPageSnapshot page = projectStudioService.getPage(projectId, pageId);
        if (page.currentResearchSessionId() == null) {
            throw new NotFoundException("当前页面还没有 research session");
        }
        int providerRank = parseSourceRank(sourceId);
        jdbcTemplate.update(
            """
            update research_sources
            set snippet = concat(coalesce(snippet, ''), '（已重试刷新）'),
                raw_payload_json = cast(? as jsonb)
            where research_session_id = ? and provider_rank = ?
            """,
            jsonText(objectNode("retry", true)),
            page.currentResearchSessionId(),
            providerRank
        );
        return getPage(projectId, pageId);
    }

    public PptDraftVersionResponse getDraft(UUID projectId, UUID pageId) {
        ProjectPageSnapshot page = projectStudioService.getPage(projectId, pageId);
        if (page.currentDraftVersionId() == null) {
            throw new NotFoundException("当前页面还没有 draft");
        }
        return jdbcTemplate.query(
            "select * from draft_versions where id = ? and project_id = ?",
            (rs, rowNum) -> new PptDraftVersionResponse(
                uuid(rs.getObject("id")),
                uuid(rs.getObject("project_id")),
                uuid(rs.getObject("page_id")),
                rs.getInt("version_no"),
                rs.getString("status"),
                uuid(rs.getObject("page_brief_version_id")),
                uuid(rs.getObject("research_session_id")),
                rs.getString("draft_svg_markup"),
                offset(rs.getTimestamp("created_at")),
                offset(rs.getTimestamp("updated_at"))
            ),
            page.currentDraftVersionId(),
            projectId
        ).stream().findFirst().orElseThrow(() -> new NotFoundException("当前页面还没有 draft"));
    }

    public PptDesignVersionResponse getDesign(UUID projectId, UUID pageId) {
        ProjectPageSnapshot page = projectStudioService.getPage(projectId, pageId);
        if (page.currentDesignVersionId() == null) {
            throw new NotFoundException("当前页面还没有 design");
        }
        return jdbcTemplate.query(
            "select * from design_versions where id = ? and project_id = ?",
            (rs, rowNum) -> new PptDesignVersionResponse(
                uuid(rs.getObject("id")),
                uuid(rs.getObject("project_id")),
                uuid(rs.getObject("page_id")),
                rs.getInt("version_no"),
                rs.getString("status"),
                uuid(rs.getObject("draft_version_id")),
                rs.getString("style_pack_id"),
                rs.getString("background_asset_path"),
                rs.getString("design_svg_markup"),
                offset(rs.getTimestamp("created_at")),
                offset(rs.getTimestamp("updated_at"))
            ),
            page.currentDesignVersionId(),
            projectId
        ).stream().findFirst().orElseThrow(() -> new NotFoundException("当前页面还没有 design"));
    }

    public PptActionJobResponse runBatchAction(UUID projectId, String actionType) {
        projectStudioService.runBatchActionByType(projectId, actionType);
        return new PptActionJobResponse("COMPLETED", UUID.randomUUID());
    }

    public PptBackgroundUploadResponse uploadBackground(UUID projectId, MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "background.bin" : file.getOriginalFilename();
        String suffix = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : ".bin";
        Path dir = Path.of("backend", "data", "backgrounds").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(projectId + suffix);
        Files.write(target, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        ProjectDetailResponse project = projectService.getProject(projectId);
        projectService.updateProject(projectId, new com.deckgo.backend.project.dto.ProjectUpdateRequest(
            project.title(),
            project.requestText(),
            project.audience(),
            project.templateId(),
            project.requestText(),
            project.pageCountTarget(),
            project.stylePreset(),
            target.toString(),
            project.workflowConstraints()
        ));
        return new PptBackgroundUploadResponse(projectId, target.toString());
    }

    public PptExportJobResponse createExport(UUID projectId, String exportFormat) throws IOException {
        String format = (exportFormat == null || exportFormat.isBlank()) ? "pptx" : exportFormat.trim().toLowerCase();
        ProjectStudioSnapshot studio = projectStudioService.getProject(projectId);
        Path dir = Path.of("backend", "data", "exports").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        UUID exportId = UUID.randomUUID();
        Path filePath = dir.resolve(exportId + ("zip".equals(format) ? ".zip" : ".pptx"));

        ArrayNode manifest = objectMapper.createArrayNode();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (ProjectPageSnapshot page : studio.pages()) {
                String svg = page.currentDesignSvg() != null && !page.currentDesignSvg().isBlank()
                    ? page.currentDesignSvg()
                    : page.currentDraftSvg();
                if (svg == null || svg.isBlank()) {
                    continue;
                }
                String entryName = page.pageCode() + ".svg";
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(svg.getBytes());
                zip.closeEntry();
                manifest.add(objectNode("pageId", page.id().toString(), "pageCode", page.pageCode(), "file", entryName));
            }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(jsonText(manifest).getBytes());
            zip.closeEntry();
        }

        OffsetDateTime now = utcNow();
        jdbcTemplate.update(
            """
            insert into export_jobs (id, project_id, export_format, status, file_path, resolved_manifest_json, created_at, updated_at)
            values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
            """,
            exportId,
            projectId,
            format,
            "COMPLETED",
            filePath.toString(),
            jsonText(manifest),
            Timestamp.from(now.toInstant()),
            Timestamp.from(now.toInstant())
        );
        return getExport(projectId, exportId);
    }

    public PptExportJobResponse getExport(UUID projectId, UUID exportId) {
        return jdbcTemplate.query(
            "select * from export_jobs where id = ? and project_id = ?",
            (rs, rowNum) -> new PptExportJobResponse(
                uuid(rs.getObject("id")),
                uuid(rs.getObject("project_id")),
                rs.getString("export_format"),
                rs.getString("status"),
                rs.getString("file_path"),
                json(rs.getString("resolved_manifest_json")),
                offset(rs.getTimestamp("created_at")),
                offset(rs.getTimestamp("updated_at"))
            ),
            exportId,
            projectId
        ).stream().findFirst().orElseThrow(() -> new NotFoundException("导出任务不存在: " + exportId));
    }

    private PptProjectSummaryResponse mapProjectSummary(ProjectResponse project) {
        ProjectStudioSnapshot studio = projectStudioService.getProject(project.id());
        return mapProjectSummary(
            new ProjectDetailResponse(
                project.id(),
                project.title(),
                project.topic(),
                project.audience(),
                project.templateId(),
                project.requestText(),
                project.currentStage(),
                null,
                project.pageCountTarget(),
                project.stylePreset(),
                project.backgroundAssetPath(),
                null,
                project.createdAt(),
                project.updatedAt()
            ),
            studio
        );
    }

    private PptProjectSummaryResponse mapProjectSummary(ProjectDetailResponse project) {
        return mapProjectSummary(project, projectStudioService.getProject(project.id()));
    }

    private PptProjectSummaryResponse mapProjectSummary(ProjectDetailResponse project, ProjectStudioSnapshot studio) {
        String previewSurface = "research";
        String previewSvg = null;
        if (!studio.pages().isEmpty()) {
            ProjectPageSnapshot first = studio.pages().get(0);
            if (first.currentDesignSvg() != null && !first.currentDesignSvg().isBlank()) {
                previewSurface = "design";
                previewSvg = first.currentDesignSvg();
            } else if (first.currentDraftSvg() != null && !first.currentDraftSvg().isBlank()) {
                previewSurface = "planning";
                previewSvg = first.currentDraftSvg();
            }
        }
        return new PptProjectSummaryResponse(
            project.id(),
            project.title(),
            project.requestText(),
            project.currentStage(),
            project.templateId(),
            previewSurface,
            previewSvg,
            project.pageCountTarget(),
            project.stylePreset(),
            project.backgroundAssetPath(),
            project.createdAt(),
            project.updatedAt()
        );
    }

    private PptRequirementFormResponse mapRequirementForm(UUID projectId, RequirementFormSnapshot snapshot) {
        JsonNode aiQuestionsRoot = snapshot.aiQuestions();
        JsonNode questionsRaw = aiQuestionsRoot != null ? aiQuestionsRoot.path("questions") : objectMapper.createArrayNode();
        JsonNode sourcesRaw = snapshot.initSearchResults() != null && snapshot.initSearchResults().path("sources").isArray()
            ? snapshot.initSearchResults().path("sources")
            : objectMapper.createArrayNode();

        List<PptRequirementQuestionResponse> questions = new ArrayList<>();
        for (JsonNode item : iterable(questionsRaw)) {
            List<PptRequirementQuestionOptionResponse> options = new ArrayList<>();
            for (JsonNode option : iterable(item.path("options"))) {
                options.add(new PptRequirementQuestionOptionResponse(
                    option.path("id").asText(option.path("optionCode").asText("")),
                    option.path("label").asText(""),
                    option.path("description").asText("")
                ));
            }
            questions.add(new PptRequirementQuestionResponse(
                item.path("id").asText(item.path("questionCode").asText("")),
                item.path("prompt").asText(item.path("label").asText("")),
                item.path("description").asText(""),
                options,
                item.path("allowCustom").asBoolean(true)
            ));
        }

        List<PptRequirementSourceResponse> sources = new ArrayList<>();
        int index = 1;
        for (JsonNode item : iterable(sourcesRaw)) {
            sources.add(new PptRequirementSourceResponse(
                item.path("id").asText("source-" + index),
                item.path("title").asText(""),
                item.path("url").asText(""),
                item.path("content").asText(item.path("snippet").asText(""))
            ));
            index++;
        }

        return new PptRequirementFormResponse(
            projectId,
            snapshot.status(),
            snapshot.summaryMd(),
            loadProjectWorkflowConstraints(projectId),
            snapshot.initSearchQueries(),
            snapshot.initSearchResults(),
            snapshot.initCorpusDigest(),
            derivedPageCountOptions(),
            snapshot.fixedItems(),
            snapshot.aiQuestions(),
            questions,
            sources,
            snapshot.answers(),
            derivedSuggestedActions(snapshot.status()),
            snapshot.createdAt(),
            snapshot.updatedAt()
        );
    }

    private PptPageResponse mapPage(UUID projectId, ProjectPageSnapshot page) {
        JsonNode brief = page.currentBrief() == null ? emptyObject() : page.currentBrief();
        JsonNode research = page.currentResearch() == null ? emptyObject() : page.currentResearch();

        List<String> contentOutline = new ArrayList<>();
        for (JsonNode bullet : iterable(brief.path("contentOutline"))) {
            contentOutline.add(bullet.asText(""));
        }
        if (contentOutline.isEmpty()) {
            for (JsonNode card : iterable(brief.path("cards"))) {
                if (!card.path("heading").asText("").isBlank()) {
                    contentOutline.add(card.path("heading").asText(""));
                }
            }
        }

        List<PptPageSearchQueryResponse> queries = new ArrayList<>();
        for (JsonNode query : iterable(research.path("queries"))) {
            if (query.isTextual()) {
                queries.add(new PptPageSearchQueryResponse(query.asText(""), "当前页 research"));
            } else {
                queries.add(new PptPageSearchQueryResponse(query.path("query_text").asText(""), query.path("query_purpose").asText("当前页 research")));
            }
        }

        List<PptPageSearchResultResponse> results = new ArrayList<>();
        int index = 1;
        for (JsonNode source : iterable(research.path("sources"))) {
            results.add(new PptPageSearchResultResponse(
                source.path("id").asText("source-" + index),
                queries.isEmpty() ? "" : queries.get(0).queryText(),
                queries.isEmpty() ? "当前页 research" : queries.get(0).queryPurpose(),
                source.path("providerRank").asInt(index),
                source.path("title").asText(""),
                source.path("url").asText(""),
                source.path("rawPayload").path("content").asText(source.path("content").asText("")),
                source.path("content").asText(""),
                "ready",
                "ready",
                source.path("sourceDocumentId").asText(null)
            ));
            index++;
        }

        List<PptCitationResponse> citations = new ArrayList<>();
        for (JsonNode citation : page.citations()) {
            citations.add(new PptCitationResponse(
                citation.path("title").asText(""),
                citation.path("url").asText(""),
                citation.path("excerpt").asText(""),
                citation.path("label").asText("")
            ));
        }

        String previewSurface = page.currentDesignSvg() != null && !page.currentDesignSvg().isBlank()
            ? "design"
            : page.currentDraftSvg() != null && !page.currentDraftSvg().isBlank()
            ? "planning"
            : "research";
        String previewSvgMarkup = "design".equals(previewSurface) ? page.currentDesignSvg() : page.currentDraftSvg();

        return new PptPageResponse(
            page.id(),
            projectId,
            page.pageCode(),
            page.pageRole(),
            page.partTitle(),
            page.sortOrder(),
            brief.path("title").asText(page.pageCode()),
            contentOutline,
            page.outlineStatus(),
            page.searchStatus(),
            page.summaryStatus(),
            page.draftStatus(),
            page.designStatus(),
            queries,
            results,
            new PptCorpusDigestResponse(
                page.currentResearchSessionId() == null ? null : page.currentResearchSessionId().toString(),
                results.size(),
                Math.max(results.size(), citations.size()),
                results.isEmpty() ? "" : results.get(results.size() - 1).title(),
                page.updatedAt() == null ? null : page.updatedAt().toString()
            ),
            research.path("findings").asText(""),
            citations,
            mapArtifactStaleness(page.artifactStaleness()),
            page.currentBriefVersionId(),
            page.currentDraftVersionId(),
            page.currentDesignVersionId(),
            page.currentDraftSvg(),
            page.currentDesignSvg(),
            previewSurface,
            previewSvgMarkup,
            page.createdAt(),
            page.updatedAt()
        );
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        List<JsonNode> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(items::add);
        }
        return items;
    }

    private String deriveTitle(String title, String requestText) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String normalized = requestText == null ? "未命名项目" : requestText.trim().replaceAll("\\s+", " ");
        return normalized.length() > 28 ? normalized.substring(0, 28) : normalized;
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    private String jsonText(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value == null ? objectMapper.nullNode() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("JSON 序列化失败", exception);
        }
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private OffsetDateTime offset(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime utcNow() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private JsonNode buildUserPayload(PptMessageCreateRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (request.uiSurface() != null) {
            payload.put("ui_surface", request.uiSurface());
        }
        payload.set("attachments", objectMapper.valueToTree(request.attachments() == null ? List.of() : request.attachments()));
        return payload;
    }

    private void appendProjectMessage(
        UUID messageId,
        UUID projectId,
        String stage,
        String scopeType,
        UUID targetPageId,
        String role,
        String contentMd,
        JsonNode payload
    ) {
        jdbcTemplate.update(
            """
            insert into project_messages (id, project_id, stage, scope_type, target_page_id, role, content_md, structured_payload_json, created_at)
            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """,
            messageId,
            projectId,
            stage,
            scopeType,
            targetPageId,
            role,
            contentMd,
            jsonText(payload),
            Timestamp.from(utcNow().toInstant())
        );
    }

    private void appendProjectEvent(
        UUID projectId,
        String eventType,
        String stage,
        String scopeType,
        UUID targetPageId,
        UUID agentRunId,
        JsonNode payload
    ) {
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
            Timestamp.from(utcNow().toInstant())
        );
    }

    private RouterDecision routeMessage(UUID projectId, String currentStage, String scopeType, UUID targetPageId, String contentMd) {
        String normalized = contentMd.toLowerCase();
        boolean pageScope = "PAGE".equalsIgnoreCase(scopeType) && targetPageId != null;

        if ("DISCOVERY".equals(currentStage)) {
            if (containsContinue(normalized)) {
                return decision(scopeType, targetPageId, currentStage, "init_confirm_to_outline", "确认初始化并生成大纲", "当前输入已经足够，直接推进到 outline。", "初始化信息已确认，系统开始生成大纲。", List.of(recommendation("project_batch_search", "继续到研究", "大纲生成后可继续推进到 research。")));
            }
            return decision(scopeType, targetPageId, currentStage, "init_update_answer", "更新初始化答案", "将当前文本记录为初始化补充说明。", "初始化补充说明已记录。", List.of(recommendation("init_confirm_to_outline", "生成大纲", "如果信息已充分，可以直接进入 outline。")));
        }

        if (pageScope) {
            if ("RESEARCH".equals(currentStage)) {
                if (normalized.contains("摘要") || normalized.contains("summary")) {
                    return decision(scopeType, targetPageId, currentStage, "page_summary_generate", "生成页面摘要", "根据当前页 research 生成 summary。", "当前页 summary 已生成。", List.of(recommendation("page_draft_generate", "生成当前页策划稿", "summary 已准备好，可以继续生成 draft。")));
                }
                if (normalized.contains("搜索") || normalized.contains("研究") || normalized.contains("资料") || normalized.contains("重搜")) {
                    return decision(scopeType, targetPageId, currentStage, "page_search_run", "执行页面研究", "为当前页执行 research。", "当前页 research 已更新。", List.of(recommendation("page_summary_generate", "生成当前页摘要", "research 完成后可继续生成 summary。")));
                }
                return decision(scopeType, targetPageId, currentStage, "page_generate_search_queries", "生成研究查询", "先为当前页生成 research 查询。", "当前页 research 查询已生成。", List.of(recommendation("page_search_run", "执行当前页研究", "查询生成后可继续执行 research。")));
            }
            if ("PLANNING".equals(currentStage)) {
                if (normalized.contains("设计")) {
                    return decision(scopeType, targetPageId, currentStage, "page_design_generate", "生成页面设计稿", "根据当前页 draft 生成 design。", "当前页设计稿已生成。", List.of());
                }
                return decision(scopeType, targetPageId, currentStage, "page_draft_generate", "生成页面策划稿", "根据当前页 research 生成 draft。", "当前页策划稿已生成。", List.of(recommendation("page_design_generate", "生成当前页设计稿", "draft 完成后可以继续生成 design。")));
            }
            if ("DESIGN".equals(currentStage)) {
                return decision(scopeType, targetPageId, currentStage, "page_design_generate", "重生成页面设计稿", "重新生成当前页 design。", "当前页设计稿已更新。", List.of());
            }
        }

        if ("OUTLINE".equals(currentStage) && containsContinue(normalized)) {
            return decision(scopeType, targetPageId, currentStage, "project_batch_search", "批量研究", "outline 已确认，推进到 research 阶段。", "项目已推进到 research 阶段。", List.of(recommendation("project_batch_draft", "继续到策划", "research 完成后可继续推进到 planning。")));
        }
        if ("RESEARCH".equals(currentStage) && containsContinue(normalized)) {
            return decision(scopeType, targetPageId, currentStage, "project_batch_draft", "批量策划", "当前项目已经完成 research，继续推进到 planning。", "项目已推进到 planning 阶段。", List.of(recommendation("project_batch_design", "继续到设计", "planning 完成后可继续推进到 design。")));
        }
        if ("PLANNING".equals(currentStage) && containsContinue(normalized)) {
            return decision(scopeType, targetPageId, currentStage, "project_batch_design", "批量设计", "当前项目已经完成 planning，继续推进到 design。", "项目已推进到 design 阶段。", List.of());
        }

        return decision(scopeType, targetPageId, currentStage, "reject", "无法执行当前消息", "当前阶段缺少可直接执行的 action_type。", "这条消息当前不能直接执行。", List.of());
    }

    private ObjectNode executeRoutedAction(UUID projectId, UUID targetPageId, RouterDecision decision, String contentMd) {
        return switch (decision.actionType()) {
            case "init_confirm_to_outline" -> {
                ProjectStudioSnapshot snapshot = confirmRequirements(projectId, new PptConfirmRequest(contentMd));
                yield objectNode("current_stage", snapshot.currentStage());
            }
            case "init_update_answer" -> {
                RequirementFormSnapshot form = projectRequirementService.getRequirementForm(projectId);
                ObjectNode answers = form.answers() instanceof ObjectNode objectNode ? objectNode.deepCopy() : objectMapper.createObjectNode();
                answers.put("freeformAnswer", contentMd);
                jdbcTemplate.update(
                    "update requirement_forms set answers_json = cast(? as jsonb), updated_at = ? where id = ?",
                    jsonText(answers),
                    Timestamp.from(utcNow().toInstant()),
                    form.id()
                );
                yield objectNode("updated", "freeformAnswer");
            }
            case "project_batch_search", "project_batch_draft", "project_batch_design" -> {
                runBatchAction(projectId, decision.actionType());
                ProjectStudioSnapshot snapshot = projectStudioService.getProject(projectId);
                yield objectNode("current_stage", snapshot.currentStage());
            }
            case "page_generate_search_queries", "page_search_run", "page_search_refresh", "page_summary_generate", "page_draft_generate", "page_design_generate" -> {
                if (targetPageId == null) {
                    throw new NotFoundException("当前消息缺少目标页面");
                }
                runPageAction(projectId, targetPageId, decision.actionType(), "page_search_refresh".equals(decision.actionType()));
                ProjectPageSnapshot page = projectStudioService.getPage(projectId, targetPageId);
                yield objectNode("page_id", page.id().toString(), "current_stage", projectStudioService.getProject(projectId).currentStage());
            }
            case "page_summary_edit" -> {
                if (targetPageId == null) {
                    throw new NotFoundException("当前消息缺少目标页面");
                }
                PptPageResponse page = patchPageSummary(projectId, targetPageId, contentMd);
                yield objectNode("page_id", page.pageId().toString(), "summary_status", page.summaryStatus());
            }
            case "reject" -> objectNode("status", "rejected");
            default -> throw new IllegalArgumentException("不支持的 action_type: " + decision.actionType());
        };
    }

    private boolean containsContinue(String normalized) {
        return normalized.contains("继续") || normalized.contains("下一步") || normalized.equals("ok") || normalized.equals("好的");
    }

    private ObjectNode recommendation(String code, String label, String reason) {
        return objectNode("code", code, "label", label, "reason", reason);
    }

    private RouterDecision decision(
        String scopeType,
        UUID targetPageId,
        String currentStage,
        String actionType,
        String stepName,
        String reason,
        String successMessage,
        List<ObjectNode> recommendations
    ) {
        return new RouterDecision(
            actionType,
            stepName,
            reason,
            successMessage,
            recommendations,
            objectNode(
                "scope_type", scopeType,
                "target_stage", currentStage,
                "target_page_id", targetPageId == null ? null : targetPageId.toString(),
                "intent_type", actionType,
                "action_type", actionType,
                "should_execute", !"reject".equals(actionType),
                "needs_clarification", false,
                "requires_confirmation", false,
                "missing_data", List.of(),
                "execution_plan", List.of(objectNode("step_code", actionType, "step_name", stepName, "reason", reason)),
                "next_recommendations", recommendations,
                "reason", reason
            )
        );
    }

    private ObjectNode buildAgentRunPayload(
        UUID agentRunId,
        String title,
        JsonNode routerDecision,
        List<ObjectNode> stepResults,
        List<ObjectNode> recommendations,
        JsonNode resultSnapshot
    ) {
        return objectNode(
            "message_kind", "agent_run",
            "agent_run_id", agentRunId.toString(),
            "title", title,
            "router_decision", routerDecision,
            "step_results", stepResults,
            "next_recommendations", recommendations,
            "result_snapshot", resultSnapshot
        );
    }

    private ObjectNode stepPayload(
        String stepCode,
        String stepName,
        String status,
        String reason,
        JsonNode result,
        String errorMessage,
        JsonNode progress
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("step_code", stepCode);
        payload.put("step_name", stepName);
        payload.put("status", status);
        payload.put("reason", reason);
        if (result != null) {
            payload.set("result", result);
        }
        if (errorMessage != null) {
            payload.put("error_message", errorMessage);
        }
        if (progress != null) {
            payload.set("progress", progress);
        }
        return payload;
    }

    private ObjectNode objectNode(Object... values) {
        ObjectNode node = objectMapper.createObjectNode();
        for (int index = 0; index + 1 < values.length; index += 2) {
            String key = String.valueOf(values[index]);
            Object value = values[index + 1];
            if (value == null) {
                node.putNull(key);
            } else if (value instanceof String stringValue) {
                node.put(key, stringValue);
            } else if (value instanceof Boolean booleanValue) {
                node.put(key, booleanValue);
            } else if (value instanceof Integer integerValue) {
                node.put(key, integerValue);
            } else if (value instanceof Long longValue) {
                node.put(key, longValue);
            } else if (value instanceof JsonNode jsonNode) {
                node.set(key, jsonNode);
            } else {
                node.set(key, objectMapper.valueToTree(value));
            }
        }
        return node;
    }

    private record RouterDecision(
        String actionType,
        String stepName,
        String reason,
        String successMessage,
        List<ObjectNode> recommendations,
        ObjectNode payload
    ) {
    }

    private JsonNode loadProjectWorkflowConstraints(UUID projectId) {
        List<String> rows = jdbcTemplate.query(
            "select workflow_constraints_json from projects where id = ?",
            (rs, rowNum) -> rs.getString(1),
            projectId
        );
        return rows.isEmpty() ? null : json(rows.get(0));
    }

    private JsonNode derivedPageCountOptions() {
        ArrayNode options = objectMapper.createArrayNode();
        options.add(objectNode("optionCode", "count-5-10", "label", "5-10 页", "pageCount", 8, "reason", "适合短汇报或快速说明"));
        options.add(objectNode("optionCode", "count-10-15", "label", "10-15 页", "pageCount", 12, "reason", "适合标准结构化介绍"));
        options.add(objectNode("optionCode", "count-15-20", "label", "15-20 页", "pageCount", 16, "reason", "适合完整讲解和深入展开"));
        return options;
    }

    private JsonNode derivedSuggestedActions(String status) {
        ArrayNode actions = objectMapper.createArrayNode();
        if ("WAITING_USER".equalsIgnoreCase(status)) {
            actions.add(objectNode("code", "fill_required_fields", "label", "补全固定项和问题答案", "reason", "先完成页数、风格和问题答案。"));
            actions.add(objectNode("code", "init_confirm_to_outline", "label", "生成大纲", "reason", "需求信息足够后可以直接进入 outline。"));
        }
        return actions;
    }

    private JsonNode mapArtifactStaleness(JsonNode existing) {
        LinkedHashMap<String, Boolean> mapped = new LinkedHashMap<>();
        if (existing == null || existing.isNull()) {
            mapped.put("search", false);
            mapped.put("summary", false);
            mapped.put("draft", false);
            mapped.put("design", false);
            return objectMapper.valueToTree(mapped);
        }
        mapped.put("search", existing.path("researchStale").asBoolean(existing.path("search").asBoolean(false)));
        mapped.put("summary", existing.path("researchStale").asBoolean(existing.path("summary").asBoolean(false)));
        mapped.put("draft", existing.path("draftStale").asBoolean(existing.path("draft").asBoolean(false)));
        mapped.put("design", existing.path("designStale").asBoolean(existing.path("design").asBoolean(false)));
        return objectMapper.valueToTree(mapped);
    }

    private ObjectNode emptyObject() {
        return objectMapper.createObjectNode();
    }

    private PptRequirementFormResponse upsertRequirementQuestion(
        UUID projectId,
        String questionCode,
        String label,
        String description,
        List<com.deckgo.backend.pptagent.dto.PptRequirementQuestionOptionInput> options,
        Boolean allowCustom
    ) {
        RequirementFormSnapshot snapshot = projectRequirementService.getRequirementForm(projectId);
        ObjectNode aiQuestions = snapshot.aiQuestions() instanceof ObjectNode objectNode ? objectNode.deepCopy() : objectMapper.createObjectNode();
        ArrayNode questions = aiQuestions.withArray("questions");
        ArrayNode next = objectMapper.createArrayNode();
        for (JsonNode node : questions) {
            String code = node.path("id").asText(node.path("questionCode").asText(""));
            if (!questionCode.equals(code)) {
                next.add(node);
            }
        }
        ObjectNode question = objectMapper.createObjectNode();
        question.put("id", questionCode);
        question.put("questionCode", questionCode);
        question.put("prompt", label);
        question.put("label", label);
        if (description != null) question.put("description", description);
        question.put("allowCustom", allowCustom == null || allowCustom);
        ArrayNode optionNodes = question.putArray("options");
        if (options != null) {
            options.forEach(option -> optionNodes.add(objectNode(
                "id", option.optionCode(),
                "optionCode", option.optionCode(),
                "label", option.label(),
                "description", option.description(),
                "value", option.value()
            )));
        }
        next.add(question);
        aiQuestions.set("questions", next);
        persistAiQuestions(projectId, aiQuestions);
        return getRequirementForm(projectId);
    }

    private void persistAiQuestions(UUID projectId, JsonNode aiQuestions) {
        jdbcTemplate.update(
            "update requirement_forms set ai_questions_json = cast(? as jsonb), updated_at = ? where project_id = ?",
            jsonText(aiQuestions),
            Timestamp.from(utcNow().toInstant()),
            projectId
        );
    }

    private int parseSourceRank(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return 1;
        }
        String normalized = sourceId.startsWith("source-") ? sourceId.substring("source-".length()) : sourceId;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }
}
