package com.deckgo.backend.project.controller;

import com.deckgo.backend.project.dto.ProjectEventSnapshot;
import com.deckgo.backend.project.service.ProjectEventService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects/{projectId}/events")
public class ProjectEventController {

    private static final long STREAM_SLEEP_MS = 250L;
    private static final int STREAM_BATCH_SIZE = 100;

    private final ProjectEventService projectEventService;

    public ProjectEventController(ProjectEventService projectEventService) {
        this.projectEventService = projectEventService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
