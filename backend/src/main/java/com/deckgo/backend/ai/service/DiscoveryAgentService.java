package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.workflow.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiscoveryAgentService extends AbstractWorkflowAgentService {

    private static final String SYSTEM_PROMPT = """
        你是 DeckGo 的 PPT 背景调研助手。
        你的任务不是直接写 PPT，而是先把需求边界问清楚。
        请沿用“忘掉一键生成，先提问”的原则，像专业顾问一样先确认用户的真实需求。
        你必须输出 3 到 5 个关键问题，每个问题提供 2 到 4 个可选项。
        问题必须覆盖：受众、是否需要竞品/对比分析、表达风格。
        还可以补充页数倾向、目标动作、必须包含的内容等问题。
        选项文案要短，描述要清楚，适合直接展示在前端卡片中。
        不要输出解释，不要输出 Markdown，只返回结构化结果。
        """;

    private final WorkflowContentService workflowContentService;

    public DiscoveryAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper,
        WorkflowContentService workflowContentService
    ) {
        super(workflowAgentClientFactory, properties, objectMapper);
        this.workflowContentService = workflowContentService;
    }

    public JsonNode generateDiscoveryCard(ProjectEntity project) {
        return useAgentOrFallback(
            "DiscoveryAgent",
            properties.getAi().getWorkflow().getDiscovery(),
            chatClient -> {
                DiscoveryCard card = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        用户的初始需求是：
                        %s

                        请生成 discovery card。
                        """.formatted(project.getTopic()))
                    .call()
                    .entity(DiscoveryCard.class);

                if (card == null || card.questions() == null || card.questions().size() < 3 || card.questions().size() > 5) {
                    throw new IllegalStateException("discovery 输出问题数量不合法");
                }

                boolean hasComparisonQuestion = card.questions().stream()
                    .map(DiscoveryQuestion::prompt)
                    .anyMatch(prompt -> prompt != null && (prompt.contains("竞品") || prompt.contains("对比")));

                if (!hasComparisonQuestion) {
                    throw new IllegalStateException("discovery 缺少竞品/对比问题");
                }

                return objectMapper.valueToTree(card);
            },
            () -> workflowContentService.generateDiscoveryCard(project)
        );
    }

    private record DiscoveryCard(String title, String description, String freeformHint, List<DiscoveryQuestion> questions) {
    }

    private record DiscoveryQuestion(String id, String prompt, List<DiscoveryOption> options) {
    }

    private record DiscoveryOption(String id, String label, String description) {
        private DiscoveryOption {
            id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        }
    }
}
