package com.deckgo.backend.render.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RendererClient {

    private final RestClient restClient;

    public RendererClient(DeckGoProperties properties) {
        this.restClient = RestClient.builder()
            .baseUrl(properties.getRendererBaseUrl())
            .build();
    }

    public RendererResponse render(RendererRequest request) {
        return restClient.post()
            .uri("/internal/render")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(RendererResponse.class);
    }

    public record RendererRequest(String jobId, String templateId, JsonNode deckSpec, String outputPath) {
    }

    public record RendererResponse(String outputPath, int slideCount) {
    }
}
