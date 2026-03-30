package com.deckgo.backend.pptagent.controller;

import com.deckgo.backend.pptagent.dto.PptActionJobResponse;
import com.deckgo.backend.pptagent.dto.PptBatchActionRequest;
import com.deckgo.backend.pptagent.dto.PptConfirmRequest;
import com.deckgo.backend.pptagent.dto.PptDesignVersionResponse;
import com.deckgo.backend.pptagent.dto.PptDraftVersionResponse;
import com.deckgo.backend.pptagent.dto.PptMessageCreateRequest;
import com.deckgo.backend.pptagent.dto.PptPageActionRequest;
import com.deckgo.backend.pptagent.dto.PptPageResponse;
import com.deckgo.backend.pptagent.dto.PptProjectCreateRequest;
import com.deckgo.backend.pptagent.dto.PptProjectSummaryResponse;
import com.deckgo.backend.pptagent.dto.PptRequirementAnswersBatchRequest;
import com.deckgo.backend.pptagent.dto.PptRequirementFormResponse;
import com.deckgo.backend.pptagent.dto.PptStoryboardPatchRequest;
import com.deckgo.backend.pptagent.dto.PptSummaryPatchRequest;
import com.deckgo.backend.pptagent.service.PptAgentApiService;
import com.deckgo.backend.project.dto.ProjectEventSnapshot;
import com.deckgo.backend.project.service.ProjectEventService;
import com.deckgo.backend.studio.dto.OutlineVersionSnapshot;
import com.deckgo.backend.studio.dto.ProjectMessageSnapshot;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/projects")
public class PptAgentProjectController {

    private static final long STREAM_SLEEP_MS = 250L;
    private static final int STREAM_BATCH_SIZE = 100;

    private final PptAgentApiService pptAgentApiService;
    private final ProjectEventService projectEventService;
    private final ObjectMapper objectMapper;

    public PptAgentProjectController(
        PptAgentApiService pptAgentApiService,
        ProjectEventService projectEventService,
        ObjectMapper objectMapper
    ) {
        this.pptAgentApiService = pptAgentApiService;
        this.projectEventService = projectEventService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<PptProjectSummaryResponse> listProjects() {
        return pptAgentApiService.listProjects();
    }

    @PostMapping
    public PptProjectSummaryResponse createProject(@Valid @RequestBody PptProjectCreateRequest request) {
        return pptAgentApiService.createProject(request);
    }

    @GetMapping("/{projectId}")
    public PptProjectSummaryResponse getProject(@PathVariable UUID projectId) {
        return pptAgentApiService.getProject(projectId);
    }

    @GetMapping("/{projectId}/messages")
    public List<ProjectMessageSnapshot> getMessages(@PathVariable UUID projectId) {
        return pptAgentApiService.listMessages(projectId);
    }

    @PostMapping("/{projectId}/messages")
    public ProjectMessageSnapshot createMessage(@PathVariable UUID projectId, @Valid @RequestBody PptMessageCreateRequest request) {
        return pptAgentApiService.createMessage(projectId, request);
    }

    @GetMapping("/{projectId}/requirements/form")
    public PptRequirementFormResponse getRequirementForm(@PathVariable UUID projectId) {
        return pptAgentApiService.getRequirementForm(projectId);
    }

    @PostMapping("/{projectId}/requirements/answers:batch")
    public PptRequirementFormResponse submitRequirementAnswers(
        @PathVariable UUID projectId,
        @Valid @RequestBody PptRequirementAnswersBatchRequest request
    ) {
        return pptAgentApiService.submitRequirementAnswers(projectId, request.answers());
    }

    @PatchMapping("/{projectId}/requirements/answers/{questionCode}")
    public PptRequirementFormResponse patchRequirementAnswer(
        @PathVariable UUID projectId,
        @PathVariable String questionCode,
        @RequestBody com.deckgo.backend.project.dto.RequirementAnswerPatchRequest request
    ) {
        return pptAgentApiService.patchRequirementAnswer(projectId, questionCode, request.value());
    }

    @PostMapping("/{projectId}/requirements/confirm")
    public ProjectStudioSnapshot confirmRequirements(
        @PathVariable UUID projectId,
        @RequestBody(required = false) PptConfirmRequest request
    ) {
        return pptAgentApiService.confirmRequirements(projectId, request);
    }

    @GetMapping("/{projectId}/outline")
    public OutlineVersionSnapshot getOutline(@PathVariable UUID projectId) {
        return pptAgentApiService.getOutline(projectId);
    }

    @PatchMapping("/{projectId}/outline/storyboard")
    public OutlineVersionSnapshot patchStoryboard(
        @PathVariable UUID projectId,
        @RequestBody PptStoryboardPatchRequest request
    ) {
        return pptAgentApiService.patchStoryboard(projectId, objectMapper.valueToTree(request.parts()));
    }

    @GetMapping("/{projectId}/pages")
    public List<PptPageResponse> listPages(@PathVariable UUID projectId) {
        return pptAgentApiService.listPages(projectId);
    }

    @GetMapping("/{projectId}/pages/{pageId}")
    public PptPageResponse getPage(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return pptAgentApiService.getPage(projectId, pageId);
    }

    @PostMapping("/{projectId}/pages/{pageId}/search-queries:generate")
    public PptActionJobResponse generatePageSearchQueries(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return pptAgentApiService.runPageAction(projectId, pageId, "page_generate_search_queries", true);
    }

    @PostMapping("/{projectId}/pages/{pageId}/search:run")
    public PptActionJobResponse runPageSearch(
        @PathVariable UUID projectId,
        @PathVariable UUID pageId,
        @RequestBody(required = false) PptPageActionRequest request
    ) {
        String actionType = request == null || request.actionType() == null || request.actionType().isBlank()
            ? "page_search_run"
            : request.actionType();
        boolean replaceExisting = request == null || request.replaceExisting() == null || request.replaceExisting();
        return pptAgentApiService.runPageAction(projectId, pageId, actionType, replaceExisting);
    }

    @PostMapping("/{projectId}/pages/{pageId}/summary:generate")
    public PptActionJobResponse generatePageSummary(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return pptAgentApiService.runPageAction(projectId, pageId, "page_summary_generate", true);
    }

    @PatchMapping("/{projectId}/pages/{pageId}/summary")
    public PptPageResponse patchPageSummary(
        @PathVariable UUID projectId,
        @PathVariable UUID pageId,
        @Valid @RequestBody PptSummaryPatchRequest request
    ) {
        return pptAgentApiService.patchPageSummary(projectId, pageId, request.summaryMd());
    }

    @PostMapping("/{projectId}/pages/{pageId}/draft:generate")
    public PptActionJobResponse generatePageDraft(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return pptAgentApiService.runPageAction(projectId, pageId, "page_draft_generate", true);
    }

    @GetMapping("/{projectId}/pages/{pageId}/draft")
    public PptDraftVersionResponse getPageDraft(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return pptAgentApiService.getDraft(projectId, pageId);
    }

    @PostMapping("/{projectId}/pages/{pageId}/design:generate")
    public PptActionJobResponse generatePageDesign(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return pptAgentApiService.runPageAction(projectId, pageId, "page_design_generate", true);
    }

    @GetMapping("/{projectId}/pages/{pageId}/design")
    public PptDesignVersionResponse getPageDesign(@PathVariable UUID projectId, @PathVariable UUID pageId) {
        return pptAgentApiService.getDesign(projectId, pageId);
    }

    @PostMapping("/{projectId}/actions/batch")
    public PptActionJobResponse runBatchAction(
        @PathVariable UUID projectId,
        @RequestBody PptBatchActionRequest request
    ) {
        return pptAgentApiService.runBatchAction(projectId, request.actionType());
    }

    @GetMapping(path = "/{projectId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
        @PathVariable UUID projectId,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean active = new AtomicBoolean(true);
        emitter.onCompletion(() -> active.set(false));
        emitter.onTimeout(() -> active.set(false));
        emitter.onError(error -> active.set(false));

        long startStreamId = parseLastEventId(lastEventId);
        ForkJoinPool.commonPool().execute(() -> streamLoop(projectId, startStreamId, emitter, active));
        return emitter;
    }

    private void streamLoop(UUID projectId, long startStreamId, SseEmitter emitter, AtomicBoolean active) {
        long currentStreamId = startStreamId;
        try {
            while (active.get()) {
                List<ProjectEventSnapshot> events = projectEventService.listEventsAfter(projectId, currentStreamId, STREAM_BATCH_SIZE);
                for (ProjectEventSnapshot event : events) {
                    emitter.send(
                        SseEmitter.event()
                            .id(String.valueOf(event.streamId()))
                            .name(event.eventType())
                            .data(event)
                    );
                    currentStreamId = event.streamId();
                }
                Thread.sleep(STREAM_SLEEP_MS);
            }
        } catch (IOException | InterruptedException exception) {
            active.set(false);
            emitter.completeWithError(exception);
            Thread.currentThread().interrupt();
        }
    }

    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
