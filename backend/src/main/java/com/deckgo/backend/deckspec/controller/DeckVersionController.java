package com.deckgo.backend.deckspec.controller;

import com.deckgo.backend.deckspec.dto.CreateDeckVersionRequest;
import com.deckgo.backend.deckspec.dto.DeckVersionResponse;
import com.deckgo.backend.deckspec.service.DeckVersionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/versions")
public class DeckVersionController {

    private final DeckVersionService deckVersionService;

    public DeckVersionController(DeckVersionService deckVersionService) {
        this.deckVersionService = deckVersionService;
    }

    @GetMapping
    public List<DeckVersionResponse> listVersions(@PathVariable UUID projectId) {
        return deckVersionService.listVersions(projectId);
    }

    @PostMapping
    public DeckVersionResponse createVersion(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateDeckVersionRequest request
    ) {
        return deckVersionService.createVersion(projectId, request);
    }
}
