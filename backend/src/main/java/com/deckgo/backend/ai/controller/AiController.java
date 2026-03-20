package com.deckgo.backend.ai.controller;

import com.deckgo.backend.ai.dto.AiDeckProposalResponse;
import com.deckgo.backend.ai.dto.CreateDeckDraftRequest;
import com.deckgo.backend.ai.dto.CreateDeckRevisionRequest;
import com.deckgo.backend.ai.service.DeckAiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final DeckAiService deckAiService;

    public AiController(DeckAiService deckAiService) {
        this.deckAiService = deckAiService;
    }

    @PostMapping("/deck-drafts")
    public AiDeckProposalResponse createDraft(@Valid @RequestBody CreateDeckDraftRequest request) {
        return deckAiService.createDraft(request);
    }

    @PostMapping("/deck-revisions")
    public AiDeckProposalResponse createRevision(@Valid @RequestBody CreateDeckRevisionRequest request) {
        return deckAiService.revise(request);
    }
}
