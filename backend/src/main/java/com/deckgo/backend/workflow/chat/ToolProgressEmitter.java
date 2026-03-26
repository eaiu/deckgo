package com.deckgo.backend.workflow.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ToolProgressEmitter {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ToolProgressEmitter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(sessionId, emitter);
            try { emitter.complete(); } catch (Exception ignored) {}
        });
        emitter.onError(e -> {
            removeEmitter(sessionId, emitter);
        });
        // Send initial comment to confirm connection
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception ignored) {
            removeEmitter(sessionId, emitter);
        }
        return emitter;
    }

    public void emit(UUID sessionId, ToolProgressEvent event) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String data;
        try {
            data = objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                    .name("tool-progress")
                    .data(data));
            } catch (Exception exception) {
                // Connection closed by client — complete emitter and remove
                try {
                    emitter.completeWithError(exception);
                } catch (Exception ignored) {
                }
                removeEmitter(sessionId, emitter);
            }
        }
    }

    private void removeEmitter(UUID sessionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(sessionId);
            }
        }
    }
}
