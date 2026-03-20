package com.deckgo.backend.ai.service;

import com.deckgo.backend.ai.dto.AiDeckProposalResponse;
import com.deckgo.backend.ai.dto.CreateDeckDraftRequest;
import com.deckgo.backend.ai.dto.CreateDeckRevisionRequest;
import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.deckspec.entity.DeckVersionEntity;
import com.deckgo.backend.deckspec.repository.DeckVersionRepository;
import com.deckgo.backend.deckspec.service.DeckSpecFactory;
import com.deckgo.backend.deckspec.service.DeckSpecSchemaService;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class DeckAiService {

    private final Optional<ChatClient.Builder> chatClientBuilder;
    private final DeckGoProperties deckGoProperties;
    private final TemplateCatalogTools templateCatalogTools;
    private final TemplateCatalogService templateCatalogService;
    private final ObjectMapper objectMapper;
    private final DeckSpecFactory deckSpecFactory;
    private final DeckSpecSchemaService deckSpecSchemaService;
    private final DeckVersionRepository deckVersionRepository;

    public DeckAiService(
        Optional<ChatClient.Builder> chatClientBuilder,
        DeckGoProperties deckGoProperties,
        TemplateCatalogTools templateCatalogTools,
        TemplateCatalogService templateCatalogService,
        ObjectMapper objectMapper,
        DeckSpecFactory deckSpecFactory,
        DeckSpecSchemaService deckSpecSchemaService,
        DeckVersionRepository deckVersionRepository
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.deckGoProperties = deckGoProperties;
        this.templateCatalogTools = templateCatalogTools;
        this.templateCatalogService = templateCatalogService;
        this.objectMapper = objectMapper;
        this.deckSpecFactory = deckSpecFactory;
        this.deckSpecSchemaService = deckSpecSchemaService;
        this.deckVersionRepository = deckVersionRepository;
    }

    public AiDeckProposalResponse createDraft(CreateDeckDraftRequest request) {
        JsonNode deckSpec = tryGenerateDraftWithAi(request).orElseGet(() -> fallbackDraft(request));
        List<String> validationMessages = validateDeckSpec(deckSpec);
        return new AiDeckProposalResponse(
            UUID.randomUUID(),
            "draft",
            deckSpec,
            "已生成候选 DeckSpec 草案，保存后才会进入正式版本历史。",
            validationMessages
        );
    }

    public AiDeckProposalResponse revise(CreateDeckRevisionRequest request) {
        DeckVersionEntity baseVersion = deckVersionRepository.findById(request.baseVersionId())
            .orElseThrow(() -> new IllegalArgumentException("基准版本不存在: " + request.baseVersionId()));

        JsonNode revisedDeck = fallbackRevision(baseVersion.getSpecJson(), request.instruction());
        List<String> validationMessages = validateDeckSpec(revisedDeck);
        return new AiDeckProposalResponse(
            UUID.randomUUID(),
            "revision",
            revisedDeck,
            "已基于现有版本生成候选修订结果，保存后才会进入正式版本历史。",
            validationMessages
        );
    }

    private Optional<JsonNode> tryGenerateDraftWithAi(CreateDeckDraftRequest request) {
        if (chatClientBuilder.isEmpty()) {
            return Optional.empty();
        }

        try {
            GeneratedDeckPlan plan = chatClientBuilder.get().build()
                .prompt()
                .system("""
                    你是 DeckGo 的 DeckSpec 规划助手。
                    你的任务不是输出 PPT 文件，而是规划出可转换为 DeckSpec 的结构化草案。
                    你可以使用模板目录工具查看当前模板。
                    输出必须简洁、具体，并贴近教学型和产品型演示文稿。
                    """)
                .user("""
                    主题：%s
                    受众：%s
                    目标：%s
                    模板：%s
                    页数提示：%s
                    请给出标题、推荐模板、以及每页的标题和一句说明。
                    """.formatted(
                    request.topic(),
                    request.audience(),
                    request.goal(),
                    request.templateId(),
                    request.slideCountHint() == null ? "未指定" : request.slideCountHint()
                ))
                .tools(templateCatalogTools)
                .call()
                .entity(GeneratedDeckPlan.class);

            if (plan == null || plan.slides() == null || plan.slides().isEmpty()) {
                return Optional.empty();
            }

            String resolvedTemplateId = plan.templateId() == null || plan.templateId().isBlank()
                ? request.templateId()
                : plan.templateId();

            templateCatalogService.getTemplate(resolvedTemplateId);

            ProjectEntity project = new ProjectEntity();
            project.setId(UUID.randomUUID());
            project.setTitle(plan.title());
            project.setTopic(request.topic());
            project.setAudience(request.audience());
            project.setTemplateId(resolvedTemplateId);

            JsonNode initialDeck = deckSpecFactory.createInitialDeckSpec(
                project.getId(),
                plan.title(),
                request.topic(),
                request.audience(),
                resolvedTemplateId
            );

            ArrayNode slides = (ArrayNode) initialDeck.path("slides");
            slides.removeAll();
            for (GeneratedSlide slide : plan.slides()) {
                slides.add(slideToDeckSpec(slide));
            }
            return Optional.of(initialDeck);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private JsonNode fallbackDraft(CreateDeckDraftRequest request) {
        JsonNode spec = deckSpecFactory.createInitialDeckSpec(
            UUID.randomUUID(),
            request.goal(),
            request.topic(),
            request.audience(),
            request.templateId()
        );
        ArrayNode slides = (ArrayNode) spec.path("slides");
        slides.add(buildTextSlide("content", "受众与目标", "受众：" + request.audience() + "。目标：" + request.goal()));
        return spec;
    }

    private JsonNode fallbackRevision(JsonNode original, String instruction) {
        ObjectNode cloned = (ObjectNode) original.deepCopy();
        ArrayNode slides = (ArrayNode) cloned.path("slides");
        if (!slides.isEmpty() && slides.get(0).isObject()) {
            ((ObjectNode) slides.get(0)).put("notes", "AI 修订指令：" + instruction);
        }
        slides.add(buildTextSlide("summary", "修订建议", instruction));
        return cloned;
    }

    private JsonNode slideToDeckSpec(GeneratedSlide slide) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", UUID.randomUUID().toString());
        node.put("kind", slide.kind() == null || slide.kind().isBlank() ? "content" : slide.kind());
        node.put("title", slide.title());
        node.put("notes", slide.summary());
        ArrayNode blocks = node.putArray("blocks");
        blocks.add(textBlock(slide.title(), "h2", 0.8, 1.0, 10.8, 1.0));
        blocks.add(textBlock(slide.summary(), "body", 0.8, 2.0, 10.8, 1.4));
        return node;
    }

    private JsonNode buildTextSlide(String kind, String title, String summary) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", UUID.randomUUID().toString());
        node.put("kind", kind);
        node.put("title", title);
        node.put("notes", summary);
        ArrayNode blocks = node.putArray("blocks");
        blocks.add(textBlock(title, "h2", 0.8, 1.0, 10.8, 1.0));
        blocks.add(textBlock(summary, "body", 0.8, 2.0, 10.8, 1.2));
        return node;
    }

    private JsonNode textBlock(String text, String level, double x, double y, double w, double h) {
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

    private List<String> validateDeckSpec(JsonNode deckSpec) {
        List<String> validationMessages = new ArrayList<>();
        try {
            deckSpecSchemaService.validate(deckSpec);
        } catch (Exception exception) {
            validationMessages.add(exception.getMessage());
        }
        return validationMessages;
    }

    private record GeneratedDeckPlan(String title, String templateId, List<GeneratedSlide> slides) {
    }

    private record GeneratedSlide(String kind, String title, String summary) {
    }
}
