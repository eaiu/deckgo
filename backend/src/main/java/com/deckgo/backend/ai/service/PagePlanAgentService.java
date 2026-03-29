package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.deckgo.backend.studio.service.PagePlanSchemaService;
import com.deckgo.backend.studio.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PagePlanAgentService extends AbstractWorkflowAgentService {

    private static final String SYSTEM_PROMPT = """
        你是 DeckGo 的页面策划助手，负责将研究成果转化为内容完备的页面策划稿。

        ## 核心原则：内容必须完备
        你输出的策划稿是”只差设计和配图”的终稿——所有文字内容必须是真实、完整、可以直接上屏的。
        绝对禁止出现以下占位符式表述：
        - “这里建议放一段…”
        - “当前页暂无…”
        - “可以补充…”
        - “此处应该是…”
        - 任何指导性、描述性的元文字

        ## 输出结构
        每个 PagePlan 必须包含：
        - pageId（与 outline 中的 page id 一致）
        - title（页面标题，真实标题）
        - goal（这一页要让读者获得什么）
        - layout（hero / two-column / three-column / comparison / timeline / bento-grid / summary）
        - visualTone（视觉语气提示）
        - speakerNotes（演讲者备注，2-3 句话）
        - cards[]（内容卡片数组，至少 1 个）

        ## cards 规则
        kind 可选值：text, metric, comparison, timeline, quote, image, highlight, chart, table

        对每张 card：
        - heading：真实标题（不是描述）
        - body：完整的段落正文。如果是 text 类型，至少 80 字的真实内容。如果是 metric，写明数据指标和解读。
        - supportingPoints：2-4 个真实要点（如适用）
        - emphasis：high / medium / low

        特殊 kind 额外字段：
        - chart → chartType（bar / line / pie）+ body 中写明图表要展示的具体数据和趋势
        - table → tableHeaders（真实列名）+ body 中写明表格数据摘要
        - image → imageIntent（具体的配图意图描述）+ body 中写明配图区域的文字说明
        - metric → body 中必须包含具体数字和单位

        ## 布局指导
        根据内容复杂度选择最合适的 layout：
        - hero：封面、过渡页、单一核心观点
        - two-column：对比、主辅信息
        - three-column：并列三项
        - bento-grid：信息密度高、多个独立信息块
        - comparison：正反对比
        - timeline：时间线叙事
        - summary：总结、行动号召

        ## 约束
        - 直接从 research 和 background 中提取事实、数据和论据填入 body
        - 不要输出 Markdown，不要解释，只返回结构化 JSON 结果
        """;

    private final WorkflowContentService workflowContentService;
    private final PagePlanSchemaService pagePlanSchemaService;
    private final TemplateCatalogTools templateCatalogTools;
    private final TemplateCatalogService templateCatalogService;

    public PagePlanAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper,
        WorkflowContentService workflowContentService,
        PagePlanSchemaService pagePlanSchemaService,
        TemplateCatalogTools templateCatalogTools,
        TemplateCatalogService templateCatalogService
    ) {
        super(workflowAgentClientFactory, properties, objectMapper);
        this.workflowContentService = workflowContentService;
        this.pagePlanSchemaService = pagePlanSchemaService;
        this.templateCatalogTools = templateCatalogTools;
        this.templateCatalogService = templateCatalogService;
    }

    public List<JsonNode> generatePagePlans(
        ProjectEntity project,
        JsonNode backgroundSummary,
        JsonNode discoveryAnswers,
        JsonNode outline,
        JsonNode pageResearch,
        String templateId
    ) {
        return useAgentOrFallback(
            "PagePlanAgent",
            properties.getAi().getWorkflow().getPagePlan(),
            chatClient -> {
                // Collect all pages from outline sections
                List<JsonNode> outlinePages = new ArrayList<>();
                if (outline.has("sections")) {
                    for (JsonNode section : outline.path("sections")) {
                        for (JsonNode page : section.path("pages")) {
                            outlinePages.add(page);
                        }
                    }
                }

                String templateJson = asJson(templateCatalogService.getTemplate(templateId));
                List<JsonNode> pagePlans = new ArrayList<>();

                for (JsonNode outlinePage : outlinePages) {
                    String pageId = outlinePage.path("id").asText("");
                    JsonNode pageResearchItem = findPageResearch(pageId, pageResearch);

                    PagePlanDocument document = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user("""
                            主题：%s

                            演示文稿标题：%s

                            背景信息摘要：%s

                            当前页大纲：
                            %s

                            当前页研究资料：
                            %s

                            当前模板：%s

                            请只输出这一页的 PagePlan（pages 数组中仅包含 1 个元素）。
                            """.formatted(
                            project.getTopic(),
                            outline.path("title").asText(""),
                            summarizeBackground(backgroundSummary),
                            asJson(outlinePage),
                            asJson(pageResearchItem),
                            templateJson
                        ))
                        .tools(templateCatalogTools)
                        .call()
                        .entity(PagePlanDocument.class);

                    if (document != null && document.pages() != null) {
                        for (PagePlanRecord page : document.pages()) {
                            JsonNode pageNode = objectMapper.valueToTree(page);
                            pagePlanSchemaService.validate(pageNode);
                            pagePlans.add(pageNode);
                        }
                    }
                }

                if (pagePlans.isEmpty()) {
                    throw new IllegalStateException("page plan 输出为空");
                }
                return pagePlans;
            },
            () -> workflowContentService.generatePagePlans(project, outline, pageResearch)
        );
    }

    private JsonNode findPageResearch(String pageId, JsonNode pageResearch) {
        if (pageResearch == null || !pageResearch.isArray()) {
            return objectMapper.createObjectNode();
        }
        for (JsonNode item : pageResearch) {
            if (pageId.equals(item.path("pageId").asText())) {
                return item;
            }
        }
        return objectMapper.createObjectNode();
    }

    private String summarizeBackground(JsonNode backgroundSummary) {
        if (backgroundSummary == null) return "无";
        String answer = backgroundSummary.path("answer").asText("");
        if (!answer.isBlank()) return answer.length() > 500 ? answer.substring(0, 500) + "..." : answer;
        String summary = backgroundSummary.path("summary").asText("");
        if (!summary.isBlank()) return summary.length() > 500 ? summary.substring(0, 500) + "..." : summary;
        return "无";
    }

    /**
     * Generate a PagePlan for a single page.
     */
    public JsonNode generateSinglePagePlan(
        ProjectEntity project,
        JsonNode backgroundSummary,
        JsonNode outlinePage,
        JsonNode pageResearchItem,
        String outlineTitle,
        String templateId
    ) {
        return useAgentOrFallback(
            "PagePlanAgent",
            properties.getAi().getWorkflow().getPagePlan(),
            chatClient -> {
                String templateJson = asJson(templateCatalogService.getTemplate(templateId));

                PagePlanDocument document = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：%s

                        演示文稿标题：%s

                        背景信息摘要：%s

                        当前页大纲：
                        %s

                        当前页研究资料：
                        %s

                        当前模板：%s

                        请只输出这一页的 PagePlan（pages 数组中仅包含 1 个元素）。
                        """.formatted(
                        project.getTopic(),
                        outlineTitle,
                        summarizeBackground(backgroundSummary),
                        asJson(outlinePage),
                        asJson(pageResearchItem),
                        templateJson
                    ))
                    .tools(templateCatalogTools)
                    .call()
                    .entity(PagePlanDocument.class);

                if (document == null || document.pages() == null || document.pages().isEmpty()) {
                    throw new IllegalStateException("single page plan 输出为空");
                }

                JsonNode pageNode = objectMapper.valueToTree(document.pages().get(0));
                pagePlanSchemaService.validate(pageNode);
                return pageNode;
            },
            () -> {
                // Fallback: create a minimal page plan
                var fallbackPlans = workflowContentService.generatePagePlans(project,
                    objectMapper.createObjectNode(), objectMapper.createArrayNode());
                return fallbackPlans.isEmpty() ? objectMapper.createObjectNode() : fallbackPlans.get(0);
            }
        );
    }

    private record PagePlanDocument(List<PagePlanRecord> pages) {
    }

    private record PagePlanRecord(
        String pageId,
        String title,
        String goal,
        String layout,
        String visualTone,
        String speakerNotes,
        List<PagePlanCardRecord> cards
    ) {
    }

    private record PagePlanCardRecord(
        String id,
        String kind,
        String heading,
        String body,
        String emphasis,
        List<String> supportingPoints,
        String chartType,
        List<String> tableHeaders,
        String imageIntent
    ) {
    }
}
