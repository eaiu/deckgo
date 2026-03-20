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
        - 特长：运用金字塔原理，结合背景调研信息构建清晰的演示逻辑

        ## Goals
        基于用户提供的 PPT 主题和背景调研信息，设计一份逻辑严密、层次清晰的 PPT 大纲。

        ## Core Methodology: 金字塔原理
        1. 结论先行：每个部分以核心观点开篇
        2. 以上统下：上层观点是下层内容的总结
        3. 归类分组：同一层级的内容属于同一逻辑范畴
        4. 逻辑递进：内容按照某种逻辑顺序展开

        ## 重要：利用调研信息
        请务必参考背景调研信息来规划大纲，使其切合当前需求上下文，而不是凭空捏造。

        ## 输出要求
        不要使用原始 [PPT_OUTLINE] 包裹格式。
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

    public JsonNode generateOutline(ProjectEntity project, JsonNode researchSummary) {
        return useAgentOrFallback(
            "OutlineAgent",
            properties.getAi().getWorkflow().getOutline(),
            chatClient -> {
                OutlineDocument outline = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        背景调研信息：
                        %s

                        页数要求：
                        5 到 7 页

                        请生成 outline。
                        """.formatted(project.getTopic(), asJson(researchSummary)))
                    .call()
                    .entity(OutlineDocument.class);

                validateOutline(outline);
                return objectMapper.valueToTree(outline);
            },
            () -> workflowContentService.generateOutline(project, researchSummary)
        );
    }

    public JsonNode reviseOutline(ProjectEntity project, JsonNode researchSummary, JsonNode currentOutline, String feedback) {
        return useAgentOrFallback(
            "OutlineAgent",
            properties.getAi().getWorkflow().getOutline(),
            chatClient -> {
                OutlineDocument outline = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        背景调研信息：
                        %s

                        当前 outline：
                        %s

                        用户反馈：
                        %s

                        请输出修订后的完整 outline，不要只输出差异。
                        """.formatted(project.getTopic(), asJson(researchSummary), asJson(currentOutline), feedback))
                    .call()
                    .entity(OutlineDocument.class);

                validateOutline(outline);
                return objectMapper.valueToTree(outline);
            },
            () -> workflowContentService.reviseOutline(project, currentOutline, feedback)
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
