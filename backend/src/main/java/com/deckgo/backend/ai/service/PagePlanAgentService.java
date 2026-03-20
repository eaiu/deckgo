package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.deckgo.backend.workflow.service.PagePlanSchemaService;
import com.deckgo.backend.workflow.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PagePlanAgentService extends AbstractWorkflowAgentService {

    private static final String SYSTEM_PROMPT = """
        你是 DeckGo 的页面策划助手。
        请直接复用“策划稿 / 数字便利贴”的思路：
        - 先明确页面目的
        - 再明确区域划分
        - 再明确哪些区域是文本、图、表、图表、时间线或对比块
        - 最后决定布局

        你的任务是根据 outline、背景信息、discovery 回答和逐页 research 结果，输出全部页面的完整 PagePlan。
        每个 PagePlan 必须包含：
        - pageId
        - title
        - goal
        - layout
        - visualTone
        - speakerNotes
        - cards[]

        cards 的 kind 可以使用：
        text, metric, comparison, timeline, quote, image, highlight, chart, table

        对 image / chart / table：
        - image 要给出 imageIntent
        - chart 要给出 chartType
        - table 要给出 tableHeaders

        风格以极简、白底、黑字、蓝色点缀为默认方向。
        不要输出 Markdown，不要解释，只返回结构化结果。
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
                PagePlanDocument document = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        背景信息：
                        %s

                        discovery 回答：
                        %s

                        outline：
                        %s

                        逐页 research：
                        %s

                        当前模板：
                        %s

                        请输出全部页面的 PagePlan。
                        """.formatted(
                        project.getTopic(),
                        asJson(backgroundSummary),
                        asJson(discoveryAnswers),
                        asJson(outline),
                        asJson(pageResearch),
                        asJson(templateCatalogService.getTemplate(templateId))
                    ))
                    .tools(templateCatalogTools)
                    .call()
                    .entity(PagePlanDocument.class);

                if (document == null || document.pages() == null || document.pages().isEmpty()) {
                    throw new IllegalStateException("page plan 输出为空");
                }

                List<JsonNode> pagePlans = new ArrayList<>();
                for (PagePlanRecord page : document.pages()) {
                    JsonNode pageNode = objectMapper.valueToTree(page);
                    pagePlanSchemaService.validate(pageNode);
                    pagePlans.add(pageNode);
                }
                return pagePlans;
            },
            () -> workflowContentService.generatePagePlans(project, outline, pageResearch)
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
