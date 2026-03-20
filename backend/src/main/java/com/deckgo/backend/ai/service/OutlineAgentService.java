package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.workflow.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OutlineAgentService extends AbstractWorkflowAgentService {

    private static final String SYSTEM_PROMPT = """
        # Role: 顶级的PPT结构架构师

        ## Profile
        - 版本：2.0 (Context-Aware)
        - 专业：PPT逻辑结构设计
        - 特长：运用金字塔原理，结合背景信息和用户回答构建清晰的演示逻辑

        ## Goals
        基于用户提供的 PPT 主题、背景摘要和 discovery 回答，设计一份逻辑严密、层次清晰的 PPT 大纲。

        ## Core Methodology: 金字塔原理
        1. 结论先行
        2. 以上统下
        3. 归类分组
        4. 逻辑递进

        ## 输出要求
        请直接返回结构化结果，字段固定为：
        - title
        - narrative
        - sections[]
        sections 下固定包含：
        - id
        - title
        - pages[]
        pages 下固定包含：
        - id
        - title
        - intent

        discovery 中页数要求必须被严格遵守。
        每一页都必须有一句页面意图说明。
        不要输出解释，不要输出 Markdown。
        """;

    private final WorkflowContentService workflowContentService;

    public OutlineAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper,
        WorkflowContentService workflowContentService
    ) {
        super(workflowAgentClientFactory, properties, objectMapper);
        this.workflowContentService = workflowContentService;
    }

    public JsonNode generateOutline(ProjectEntity project, JsonNode backgroundSummary, JsonNode discoveryAnswers) {
        return useAgentOrFallback(
            "OutlineAgent",
            properties.getAi().getWorkflow().getOutline(),
            chatClient -> {
                OutlineDocument outline = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        背景信息：
                        %s

                        discovery 回答：
                        %s

                        请生成 outline。
                        """.formatted(project.getTopic(), asJson(backgroundSummary), asJson(discoveryAnswers)))
                    .call()
                    .entity(OutlineDocument.class);

                validateOutline(outline);
                return objectMapper.valueToTree(outline);
            },
            () -> workflowContentService.generateOutline(project, backgroundSummary, discoveryAnswers)
        );
    }

    public JsonNode reviseOutline(
        ProjectEntity project,
        JsonNode backgroundSummary,
        JsonNode discoveryAnswers,
        JsonNode currentOutline,
        String feedback
    ) {
        return useAgentOrFallback(
            "OutlineAgent",
            properties.getAi().getWorkflow().getOutline(),
            chatClient -> {
                OutlineDocument outline = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        背景信息：
                        %s

                        discovery 回答：
                        %s

                        当前 outline：
                        %s

                        用户反馈：
                        %s

                        请输出修订后的完整 outline，不要只输出差异。
                        """.formatted(project.getTopic(), asJson(backgroundSummary), asJson(discoveryAnswers), asJson(currentOutline), feedback))
                    .call()
                    .entity(OutlineDocument.class);

                validateOutline(outline);
                return objectMapper.valueToTree(outline);
            },
            () -> workflowContentService.reviseOutline(project, backgroundSummary, discoveryAnswers, currentOutline, feedback)
        );
    }

    private void validateOutline(OutlineDocument outline) {
        if (outline == null || outline.sections() == null || outline.sections().isEmpty()) {
            throw new IllegalStateException("outline 结构为空");
        }
        boolean invalidPage = outline.sections().stream()
            .flatMap(section -> section.pages().stream())
            .anyMatch(page -> page.title() == null || page.title().isBlank() || page.intent() == null || page.intent().isBlank());
        if (invalidPage) {
            throw new IllegalStateException("outline 页面缺少 title 或 intent");
        }
    }

    private record OutlineDocument(String title, String narrative, List<OutlineSection> sections) {
    }

    private record OutlineSection(String id, String title, List<OutlinePage> pages) {
    }

    private record OutlinePage(String id, String title, String intent) {
    }
}
