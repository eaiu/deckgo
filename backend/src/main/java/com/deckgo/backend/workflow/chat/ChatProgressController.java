package com.deckgo.backend.workflow.chat;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/workflow-sessions")
public class ChatProgressController {

    private final ToolProgressEmitter toolProgressEmitter;

    public ChatProgressController(ToolProgressEmitter toolProgressEmitter) {
        this.toolProgressEmitter = toolProgressEmitter;
    }

    @GetMapping("/{sessionId}/progress")
    public SseEmitter progress(@PathVariable UUID sessionId) {
        return toolProgressEmitter.subscribe(sessionId);
    }
}
