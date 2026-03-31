package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TavilySearchService {

    private static final int FIXED_MAX_RESULTS = 1;
    private static final Logger log = LoggerFactory.getLogger(TavilySearchService.class);

    private final DeckGoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TavilySearchService(DeckGoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
            .baseUrl(properties.getAi().getTavily().getBaseUrl())
            .requestFactory(factory)
            .build();
    }

    public Optional<JsonNode> collectBackgroundSummary(String topic) {
        String query = topic.strip() + " 是什么";
        return executeSearch(query, properties.getAi().getTavily().getSearchDepth())
            .map(response -> normalize("background", query, response));
    }

    public Optional<JsonNode> collectPageResearch(String query, String searchDepth) {
        return executeSearch(query, searchDepth)
            .map(response -> normalize("page", query, response));
    }

    private Optional<JsonNode> executeSearch(String query, String searchDepth) {
        DeckGoProperties.Tavily tavily = properties.getAi().getTavily();
        if (!tavily.isEnabled() || tavily.getApiKey() == null || tavily.getApiKey().isBlank()) {
            return Optional.empty();
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query);
        body.put("search_depth", searchDepth == null || searchDepth.isBlank() ? tavily.getSearchDepth() : searchDepth);
        body.put("topic", tavily.getTopic());
        body.put("max_results", FIXED_MAX_RESULTS);
        body.put("include_answer", "advanced");
        body.put("include_favicon", true);
        body.put("include_images", false);
        body.put("auto_parameters", false);

        try {
            JsonNode response = restClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + tavily.getApiKey())
                .body(body)
                .retrieve()
                .body(JsonNode.class);

            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Tavily search failed for query [{}]: {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode normalize(String kind, String query, JsonNode response) {
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("kind", kind);
        normalized.put("query", query);
        normalized.put("answer", response.path("answer").asText(""));
        normalized.put("requestId", response.path("request_id").asText(""));
        normalized.put("responseTime", response.path("response_time").asText(""));
        normalized.put("searchDepth", response.path("search_depth").asText(properties.getAi().getTavily().getSearchDepth()));
        normalized.put("maxResults", FIXED_MAX_RESULTS);

        ArrayNode sources = normalized.putArray("sources");
        for (JsonNode result : response.path("results")) {
            ObjectNode source = sources.addObject();
            source.put("title", result.path("title").asText(""));
            source.put("url", result.path("url").asText(""));
            source.put("content", result.path("content").asText(""));
            source.put("score", result.path("score").asDouble(0));
            source.put("favicon", result.path("favicon").asText(""));
        }

        return normalized;
    }
}
