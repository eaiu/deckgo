package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.deckgo.backend.workflow.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SvgDesignAgentService extends AbstractWorkflowAgentService {

    private static final Pattern SVG_PATTERN = Pattern.compile("(?s)<svg\\b.*?</svg>");
    private static final Pattern VIEW_BOX_PATTERN = Pattern.compile("viewBox\\s*=\\s*['\"]0\\s+0\\s+1280\\s+720['\"]");

    private static final String SYSTEM_PROMPT = """
        作为精通信息架构与 SVG 编码的专家，你的任务是将完整的文字内容转化为一张高质量、结构化、具备高级感、简洁感和专业感的 SVG 演示文稿页面。

        1. 画布：SVG viewBox 必须是 0 0 1280 720。

        2. 内容页的便当网格 (Bento Grid) 布局
        这是一种灵活的网格系统，其布局应由内容本身的需求驱动，而非僵硬的模板。通过组合不同尺寸的卡片，创造出动态且视觉有趣的布局。
        - 核心原则：
          - 灵活性：卡片数量不固定，可以根据内容需要变化。
          - 层级感：使用卡片尺寸建立视觉层级，最重要的信息放在最大的卡片上。
          - 留白：在所有卡片之间保持至少 20px 的间距。
        - 布局组合示例：
          - 单一焦点：一张大卡片覆盖大部分区域。
          - 两栏布局：50/50 对称或 2/3 + 1/3 非对称。
          - 三栏布局：三张等宽卡片。
          - 主次结合：一张大的居中卡片，两侧小卡片。
          - 顶部英雄式：顶部一张宽幅英雄卡片，下方是若干小卡片。
          - 混合网格：自由混合不同尺寸的卡片。

        3. 当前项目的额外约束
        - 风格固定为：白底、黑字、蓝色点缀、轻边框、强留白。
        - 不要使用深色背景、玻璃拟态、霓虹渐变、紫色主视觉。
        - 不要凭空新增大量内容，只能根据页面策划稿组织布局和视觉层级。

        4. 输出要求
        - 只能输出单个完整的 <svg>...</svg>
        - 不要输出 Markdown
        - 不要输出解释文字
        """;

    private final WorkflowContentService workflowContentService;
    private final TemplateCatalogTools templateCatalogTools;
    private final TemplateCatalogService templateCatalogService;

    public SvgDesignAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper,
        WorkflowContentService workflowContentService,
        TemplateCatalogTools templateCatalogTools,
        TemplateCatalogService templateCatalogService
    ) {
        super(workflowAgentClientFactory, properties, objectMapper);
        this.workflowContentService = workflowContentService;
        this.templateCatalogTools = templateCatalogTools;
        this.templateCatalogService = templateCatalogService;
    }

    public String generateFinalSvg(JsonNode pagePlan, String templateId) {
        return useAgentOrFallback(
            "SvgDesignAgent",
            properties.getAi().getWorkflow().getSvgDesign(),
            chatClient -> {
                String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        页面策划稿：
                        %s

                        模板样式 token：
                        %s

                        请输出最终 SVG。
                        """.formatted(asJson(pagePlan), asJson(templateCatalogService.getTemplate(templateId))))
                    .tools(templateCatalogTools)
                    .call()
                    .content();

                String svg = extractSvg(response);
                if (!VIEW_BOX_PATTERN.matcher(svg).find()) {
                    throw new IllegalStateException("SVG 缺少固定 viewBox");
                }
                return svg;
            },
            () -> workflowContentService.renderFinalSvg(pagePlan, templateId)
        );
    }

    private String extractSvg(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("SVG 输出为空");
        }

        Matcher matcher = SVG_PATTERN.matcher(response);
        if (!matcher.find()) {
            throw new IllegalStateException("未提取到合法 SVG");
        }
        return matcher.group();
    }
}
