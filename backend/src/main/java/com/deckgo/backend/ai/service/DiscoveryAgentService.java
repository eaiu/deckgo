package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.workflow.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiscoveryAgentService extends AbstractWorkflowAgentService {

    private static final String SYSTEM_PROMPT = """
        你是 DeckGo 的背景调研助手。
        在这一步，系统已经先通过搜索知道了主题的大体背景。
        你的任务不是直接写 PPT，而是基于“主题 + 背景摘要”生成一组补充问题卡片。
        固定必问项只有两类：
        1. 页数
        2. 使用场景

        其中：
        - 页数问题不要由你生成，系统会固定插入。
        - 你需要生成“使用场景”这一题的选项。
        - 你还可以额外补充 0 到 2 个问题，但不要再重复问页数。

        输出必须适合前端直接展示，不要输出 Markdown，不要解释。
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

    public JsonNode generateDiscoveryCard(ProjectEntity project, JsonNode backgroundSummary) {
        return useAgentOrFallback(
            "DiscoveryAgent",
            properties.getAi().getWorkflow().getDiscovery(),
            chatClient -> {
                DiscoveryPlan plan = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        背景摘要：
                        %s

                        请生成 discovery 补充问题，重点是“使用场景”选项和最多 2 个附加问题。
                        """.formatted(project.getTopic(), asJson(backgroundSummary)))
                    .call()
                    .entity(DiscoveryPlan.class);

                if (plan == null || plan.usageScenarioOptions() == null || plan.usageScenarioOptions().isEmpty()) {
                    throw new IllegalStateException("discovery 使用场景选项为空");
                }

                return buildCard(plan, backgroundSummary);
            },
            () -> workflowContentService.generateDiscoveryCard(project, backgroundSummary)
        );
    }

    private JsonNode buildCard(DiscoveryPlan plan, JsonNode backgroundSummary) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", blankToDefault(plan.title(), "先确认几个关键问题"));
        root.put("description", blankToDefault(plan.description(), backgroundSummary.path("summary").asText("先补齐需求边界，再继续往大纲推进。")));
        root.put("freeformHint", blankToDefault(plan.freeformHint(), "如果你还有特别要求，可以继续补充，例如必须出现的内容或特别想强调的重点。"));

        ArrayNode questions = root.putArray("questions");
        questions.add(fixedPageCountQuestion());
        questions.add(dynamicScenarioQuestion(plan.usageScenarioPrompt(), plan.usageScenarioOptions()));

        if (plan.additionalQuestions() != null) {
            for (DiscoveryQuestion question : plan.additionalQuestions()) {
                if (question.prompt() != null && !question.prompt().contains("页数")) {
                    questions.add(objectMapper.valueToTree(question));
                }
            }
        }

        return root;
    }

    private ObjectNode fixedPageCountQuestion() {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("id", "page-count");
        question.put("prompt", "你希望最终页数大概落在哪个区间？");
        ArrayNode options = question.putArray("options");
        options.add(option("count-5-10", "5-10", "适合短汇报或快速说明"));
        options.add(option("count-10-15", "10-15", "适合标准结构化介绍"));
        options.add(option("count-15-20", "15-20", "适合完整讲解和深入展开"));
        options.add(option("count-free", "自由发挥", "由系统按内容判断"));
        return question;
    }

    private ObjectNode dynamicScenarioQuestion(String prompt, List<DiscoveryOption> optionsFromAi) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("id", "usage-scenario");
        question.put("prompt", blankToDefault(prompt, "这份 PPT 主要会用在什么场景？"));
        ArrayNode options = question.putArray("options");
        for (DiscoveryOption option : optionsFromAi) {
            options.add(objectMapper.valueToTree(option));
        }
        return question;
    }

    private ObjectNode option(String id, String label, String description) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("id", id);
        option.put("label", label);
        option.put("description", description);
        return option;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record DiscoveryPlan(
        String title,
        String description,
        String freeformHint,
        String usageScenarioPrompt,
        List<DiscoveryOption> usageScenarioOptions,
        List<DiscoveryQuestion> additionalQuestions
    ) {
    }

    private record DiscoveryQuestion(String id, String prompt, List<DiscoveryOption> options) {
        private DiscoveryQuestion {
            id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        }
    }

    private record DiscoveryOption(String id, String label, String description) {
        private DiscoveryOption {
            id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        }
    }
}
