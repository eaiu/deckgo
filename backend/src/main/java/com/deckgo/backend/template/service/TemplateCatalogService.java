package com.deckgo.backend.template.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.template.dto.TemplateSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TemplateCatalogService {

    private final ObjectMapper objectMapper;
    private final Path catalogPath;

    public TemplateCatalogService(ObjectMapper objectMapper, DeckGoProperties properties) {
        this.objectMapper = objectMapper;
        this.catalogPath = Path.of(properties.getContractsDir(), "templates", "catalog.json").normalize();
    }

    public List<TemplateSummary> listTemplates() {
        JsonNode root = readCatalog();
        List<TemplateSummary> templates = new ArrayList<>();
        for (JsonNode template : root.path("templates")) {
            templates.add(new TemplateSummary(
                template.path("id").asText(),
                template.path("name").asText(),
                template.path("description").asText(),
                objectMapper.convertValue(template.path("slideKinds"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                template.path("defaultTheme")
            ));
        }
        return templates;
    }

    public TemplateSummary getTemplate(String templateId) {
        return listTemplates().stream()
            .filter(template -> template.id().equals(templateId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("模板不存在: " + templateId));
    }

    public JsonNode readCatalog() {
        try {
            return objectMapper.readTree(Files.readString(catalogPath));
        } catch (IOException exception) {
            throw new IllegalStateException("读取模板目录失败: " + catalogPath, exception);
        }
    }
}
