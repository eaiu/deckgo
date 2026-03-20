package com.deckgo.backend.deckspec.service;

import com.deckgo.backend.template.dto.TemplateSummary;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeckSpecFactory {

    private final ObjectMapper objectMapper;
    private final TemplateCatalogService templateCatalogService;

    public DeckSpecFactory(ObjectMapper objectMapper, TemplateCatalogService templateCatalogService) {
        this.objectMapper = objectMapper;
        this.templateCatalogService = templateCatalogService;
    }

    public JsonNode createInitialDeckSpec(UUID projectId, String title, String topic, String audience, String templateId) {
        TemplateSummary template = templateCatalogService.getTemplate(templateId);

        ObjectNode deck = objectMapper.createObjectNode();
        deck.put("version", "1.0.0");
        deck.put("deckId", projectId.toString());
        deck.put("title", title);
        deck.put("templateId", template.id());

        ObjectNode theme = deck.putObject("theme");
        theme.put("id", template.id());
        theme.put("name", template.name());
        theme.set("palette", template.defaultTheme());

        ArrayNode slides = deck.putArray("slides");
        slides.add(buildTitleSlide(title, topic, audience));
        slides.add(buildSummarySlide(topic));
        return deck;
    }

    private JsonNode buildTitleSlide(String title, String topic, String audience) {
        ObjectNode slide = objectMapper.createObjectNode();
        slide.put("id", UUID.randomUUID().toString());
        slide.put("kind", "title");
        slide.put("title", title);
        slide.put("notes", "初始化项目时自动生成的标题页。");

        ArrayNode blocks = slide.putArray("blocks");
        blocks.add(textBlock("h1", title, 0.8, 0.9, 11.0, 1.3));
        blocks.add(textBlock("body", "主题：" + topic, 0.8, 2.1, 10.8, 0.8));
        blocks.add(textBlock("body", "受众：" + audience, 0.8, 2.8, 10.8, 0.8));
        return slide;
    }

    private JsonNode buildSummarySlide(String topic) {
        ObjectNode slide = objectMapper.createObjectNode();
        slide.put("id", UUID.randomUUID().toString());
        slide.put("kind", "summary");
        slide.put("title", "下一步");
        slide.put("notes", "初始化项目时自动生成的总结页。");

        ArrayNode blocks = slide.putArray("blocks");
        blocks.add(textBlock("h2", "围绕主题推进", 0.8, 1.0, 8.0, 1.0));
        blocks.add(textBlock("body", "后续可以通过 AI 草案、JSON 编辑和版本保存逐步完善这份 DeckSpec。当前主题是：" + topic, 0.8, 2.0, 11.0, 1.2));
        return slide;
    }

    private JsonNode textBlock(String level, String text, double x, double y, double w, double h) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("id", UUID.randomUUID().toString());
        block.put("kind", "text");
        ObjectNode frame = block.putObject("frame");
        frame.put("x", x);
        frame.put("y", y);
        frame.put("w", w);
        frame.put("h", h);
        ObjectNode content = block.putObject("content");
        content.put("text", text);
        content.put("level", level);
        return block;
    }
}
