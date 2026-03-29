package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.studio.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class ResearchAgentService extends AbstractWorkflowAgentService {

    private static final String SYSTEM_PROMPT = """
        你是 DeckGo 的逐页资料搜集助手。
        当前大纲已经确定，你的任务是判断每一页是否需要调用搜索，并为需要搜索的页面生成检索计划。

        你必须输出 pages[]，每一页都包含：
        - pageId
        - title
        - needsSearch
        - searchIntent
        - queries[]
        - searchDepth

        要求：
        - 如果页面是封面、目录、纯总结页，可以不搜索
        - 如果页面需要事实、案例、定义、历史、市场、技术细节，就应该搜索
        - queries 最多 3 个
        - searchDepth 只能是 basic 或 advanced
        - 不要压缩为一句总摘要，要保留对后续策划有价值的研究上下文
        - 不要输出 Markdown，不要解释
        """;

    private final WorkflowContentService workflowContentService;
    private final TavilySearchService tavilySearchService;
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    public ResearchAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper,
        WorkflowContentService workflowContentService,
        TavilySearchService tavilySearchService
    ) {
        super(workflowAgentClientFactory, properties, objectMapper);
        this.workflowContentService = workflowContentService;
        this.tavilySearchService = tavilySearchService;
    }

    public JsonNode generatePageResearch(
        ProjectEntity project,
        JsonNode backgroundSummary,
        JsonNode discoveryAnswers,
        JsonNode outline
    ) {
        return useAgentOrFallback(
            "ResearchAgent",
            properties.getAi().getWorkflow().getResearch(),
            chatClient -> {
                PageResearchPlanDocument plan = chatClient.prompt()
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

                        请输出逐页 research plan。
                        """.formatted(project.getTopic(), asJson(backgroundSummary), asJson(discoveryAnswers), asJson(outline)))
                    .call()
                    .entity(PageResearchPlanDocument.class);

                if (plan == null || plan.pages() == null || plan.pages().isEmpty()) {
                    throw new IllegalStateException("page research plan 为空");
                }

                return enrichWithSearch(plan.pages());
            },
            () -> workflowContentService.generatePageResearch(project, outline)
        );
    }

    private JsonNode enrichWithSearch(List<PageResearchPlanItem> pages) {
        List<CompletableFuture<ObjectNode>> futures = pages.stream()
            .map(page -> CompletableFuture.supplyAsync(() -> enrichSinglePage(page), ioPool))
            .toList();

        ArrayNode enriched = objectMapper.createArrayNode();
        futures.forEach(future -> enriched.add(future.join()));
        return enriched;
    }

    private ObjectNode enrichSinglePage(PageResearchPlanItem page) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("pageId", page.pageId());
        item.put("title", page.title());
        item.put("needsSearch", page.needsSearch());
        item.put("searchIntent", page.searchIntent());
        item.put("searchDepth", page.searchDepth() == null || page.searchDepth().isBlank() ? "basic" : page.searchDepth());

        ArrayNode queries = item.putArray("queries");
        List<String> searchQueries = page.queries() == null ? List.of() : page.queries().stream().filter(query -> query != null && !query.isBlank()).limit(3).toList();
        searchQueries.forEach(queries::add);

        ArrayNode sources = item.putArray("sources");
        StringBuilder findings = new StringBuilder();

        if (page.needsSearch() && !searchQueries.isEmpty()) {
            for (String query : searchQueries) {
                Optional<JsonNode> searchResult = tavilySearchService.collectPageResearch(query, item.path("searchDepth").asText());
                if (searchResult.isPresent()) {
                    JsonNode result = searchResult.get();
                    if (!result.path("answer").asText("").isBlank()) {
                        if (!findings.isEmpty()) {
                            findings.append(" ");
                        }
                        findings.append(result.path("answer").asText(""));
                    }
                    for (JsonNode source : result.path("sources")) {
                        sources.add(source);
                    }
                }
            }
        }

        item.put("findings", findings.toString().isBlank() ? "当前页暂无外部搜索摘要，后续以原始 sources 为主要参考。" : findings.toString());
        return item;
    }

    private record PageResearchPlanDocument(List<PageResearchPlanItem> pages) {
    }

    private record PageResearchPlanItem(
        String pageId,
        String title,
        boolean needsSearch,
        String searchIntent,
        List<String> queries,
        String searchDepth
    ) {
    }
}
