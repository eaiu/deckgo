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
        - 不要直接做最终设计
        - 每一页先变成一张数字便利贴
        - 先明确这页想表达什么，再决定用什么布局和卡片组织信息

        你的任务是根据 outline 输出全部页面的 PagePlan。
        每个 PagePlan 必须包含：
        - pageId
        - title
        - goal
        - layout
        - visualTone
        - speakerNotes
        - cards[]

        layout 只能使用：
        hero, two-column, three-column, comparison, timeline, bento-grid, summary

        每个 card 必须包含：
        - id
        - kind
        - heading
        - body
        - emphasis

        cards 的 body 必须是完整句子。
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

    public List<JsonNode> generatePagePlans(ProjectEntity project, JsonNode outline, String templateId) {
        return useAgentOrFallback(
            "PagePlanAgent",
            properties.getAi().getWorkflow().getPagePlan(),
            chatClient -> {
                PagePlanDocument document = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        outline：
                        %s

                        当前模板：
                        %s

                        请输出全部页面的 PagePlan。
                        """.formatted(
                        project.getTopic(),
                        asJson(outline),
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
            () -> workflowContentService.generatePagePlans(project, outline)
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
        List<String> supportingPoints
    ) {
    }
}
