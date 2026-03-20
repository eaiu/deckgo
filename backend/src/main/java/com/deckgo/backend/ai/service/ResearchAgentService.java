package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.workflow.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResearchAgentService extends AbstractWorkflowAgentService {

    private static final String SYSTEM_PROMPT = """
        你是 DeckGo 的资料整理助手。
        你当前不能访问外部搜索，只能根据用户主题和调研回答做高质量整理。
        请沿用“资料搜集先服务大纲”的思路，把当前已知信息整理成能直接供大纲使用的 research summary。
        不要编造引用或虚构来源，不要假装已经做过外部搜索。
        必须给出：audience、summary、assumptions、comparisonPoints、keyFindings、suggestedTemplateId、titleSuggestion。
        suggestedTemplateId 只能从 clarity-blue、paper-grid、studio-dark 中选择一个。
        结果要简洁、可信、可执行，不要输出 Markdown，只返回结构化结果。
        """;

    private final WorkflowContentService workflowContentService;

    public ResearchAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper,
        WorkflowContentService workflowContentService
    ) {
        super(workflowAgentClientFactory, properties, objectMapper);
        this.workflowContentService = workflowContentService;
    }

    public JsonNode generateResearchSummary(ProjectEntity project, JsonNode discoveryAnswers) {
        return useAgentOrFallback(
            "ResearchAgent",
            properties.getAi().getWorkflow().getResearch(),
            chatClient -> {
                ResearchSummary summary = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        主题：
                        %s

                        discovery 回答：
                        %s

                        请整理成 research summary。
                        """.formatted(project.getTopic(), asJson(discoveryAnswers)))
                    .call()
                    .entity(ResearchSummary.class);

                if (summary == null || summary.summary() == null || summary.summary().isBlank()) {
                    throw new IllegalStateException("research summary 为空");
                }

                String templateId = summary.suggestedTemplateId();
                if (!List.of("clarity-blue", "paper-grid", "studio-dark").contains(templateId)) {
                    throw new IllegalStateException("research 建议模板不合法: " + templateId);
                }

                return objectMapper.valueToTree(summary);
            },
            () -> workflowContentService.generateResearchSummary(project, discoveryAnswers)
        );
    }

    private record ResearchSummary(
        String audience,
        String summary,
        String suggestedTemplateId,
        String titleSuggestion,
        List<String> assumptions,
        List<String> comparisonPoints,
        List<String> keyFindings
    ) {
    }
}
