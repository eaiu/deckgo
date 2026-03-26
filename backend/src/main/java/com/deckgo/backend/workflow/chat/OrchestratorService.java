package com.deckgo.backend.workflow.chat;

import com.deckgo.backend.ai.service.WorkflowAgentClientFactory;
import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.project.service.ProjectService;
import com.deckgo.backend.workflow.dto.WorkflowSessionResponse;
import com.deckgo.backend.workflow.entity.WorkflowMessageEntity;
import com.deckgo.backend.workflow.entity.WorkflowSessionEntity;
import com.deckgo.backend.workflow.enums.WorkflowMessageRole;
import com.deckgo.backend.workflow.enums.WorkflowSessionStatus;
import com.deckgo.backend.workflow.repository.WorkflowMessageRepository;
import com.deckgo.backend.workflow.repository.WorkflowPageRepository;
import com.deckgo.backend.workflow.repository.WorkflowSessionRepository;
import com.deckgo.backend.workflow.service.WorkflowSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorService {

    private static final int MAX_HISTORY_MESSAGES = 20;

    private final WorkflowAgentClientFactory clientFactory;
    private final DeckGoProperties properties;
    private final OrchestratorTools orchestratorTools;
    private final WorkflowSessionService sessionService;
    private final WorkflowSessionRepository sessionRepository;
    private final WorkflowMessageRepository messageRepository;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public OrchestratorService(
        WorkflowAgentClientFactory clientFactory,
        DeckGoProperties properties,
        OrchestratorTools orchestratorTools,
        WorkflowSessionService sessionService,
        WorkflowSessionRepository sessionRepository,
        WorkflowMessageRepository messageRepository,
        ProjectService projectService,
        ObjectMapper objectMapper
    ) {
        this.clientFactory = clientFactory;
        this.properties = properties;
        this.orchestratorTools = orchestratorTools;
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    public com.deckgo.backend.workflow.chat.ChatResponse chat(UUID sessionId, ChatRequest request) {
        WorkflowSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("工作流会话不存在: " + sessionId));
        ProjectEntity project = projectService.findEntity(session.getProjectId());

        // Mark session as PROCESSING so frontend knows work is in-flight
        session.setStatus(WorkflowSessionStatus.PROCESSING);
        sessionRepository.save(session);

        appendMessage(sessionId, WorkflowMessageRole.USER, session.getCurrentStage(),
            textContent(request.message()), null, "CHAT");

        Optional<ChatClient> clientOpt = clientFactory.create(
            properties.getAi().getWorkflow().getOrchestrator());

        String assistantText;
        List<ToolCallStep> toolCalls = List.of();

        try {
            if (clientOpt.isPresent()) {
                ChatClient client = clientOpt.get();
                String systemPrompt = buildSystemPrompt(session, project);

                ChatResponse aiResponse = client.prompt()
                    .system(systemPrompt)
                    .user(request.message())
                    .tools(orchestratorTools)
                    .call()
                    .chatResponse();

                assistantText = extractContent(aiResponse);
                toolCalls = extractToolCalls(aiResponse);
            } else {
                assistantText = "Orchestrator 不可用（API key 未配置或 agent 已禁用）。请使用传统的命令模式。";
            }
        } catch (Exception exception) {
            // Restore status on failure
            session = sessionRepository.findById(sessionId).orElse(session);
            if (session.getStatus() == WorkflowSessionStatus.PROCESSING) {
                session.setStatus(WorkflowSessionStatus.WAITING_USER);
                session.setLastError(exception.getMessage());
                sessionRepository.save(session);
            }
            throw exception;
        }

        // Restore to WAITING_USER if tools didn't set a terminal status
        session = sessionRepository.findById(sessionId).orElse(session);
        if (session.getStatus() == WorkflowSessionStatus.PROCESSING) {
            session.setStatus(WorkflowSessionStatus.WAITING_USER);
            sessionRepository.save(session);
        }

        JsonNode toolCallsJson = toolCalls.isEmpty() ? null : objectMapper.valueToTree(toolCalls);
        appendMessage(sessionId, WorkflowMessageRole.ASSISTANT, session.getCurrentStage(),
            textContent(assistantText), toolCallsJson, "CHAT");

        WorkflowSessionResponse snapshot = sessionService.getSession(sessionId);

        return new com.deckgo.backend.workflow.chat.ChatResponse(
            sessionId, assistantText, toolCalls, snapshot);
    }

    private String buildSystemPrompt(WorkflowSessionEntity session, ProjectEntity project) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            你是 DeckGo 的 PPT 设计助手。用户通过对话与你协作，你负责理解用户意图，并调用合适的工具来完成任务。
            所有工具调用都需要传入 sessionId 参数。

            ## 当前会话信息
            """);
        sb.append("- sessionId: %s\n".formatted(session.getId()));
        sb.append("- 项目: %s（主题: %s）\n".formatted(project.getTitle(), project.getTopic()));
        sb.append("- 当前阶段: %s\n".formatted(session.getCurrentStage()));
        sb.append("- 状态: %s\n".formatted(session.getStatus()));

        if (session.getOutlineJson() != null) {
            JsonNode outline = session.getOutlineJson();
            int pageCount = 0;
            if (outline.has("sections")) {
                for (JsonNode s : outline.path("sections")) {
                    pageCount += s.has("pages") ? s.path("pages").size() : 0;
                }
            }
            sb.append("- 大纲: 「%s」，%d 个章节，%d 页\n".formatted(
                outline.path("title").asText(""), outline.path("sections").size(), pageCount));
        } else {
            sb.append("- 大纲: 未生成\n");
        }

        if (session.getPageResearchJson() != null && session.getPageResearchJson().isArray()) {
            sb.append("- 逐页资料: 已搜集（%d 页）\n".formatted(session.getPageResearchJson().size()));
        } else {
            sb.append("- 逐页资料: 未搜集\n");
        }

        if (session.getCurrentVersionId() != null) {
            sb.append("- 页面版本: 已有设计版本\n");
        } else {
            sb.append("- 页面: 未生成\n");
        }

        // Tell LLM explicitly what "继续" means for the current stage
        String nextAction;
        switch (session.getCurrentStage()) {
            case DISCOVERY:
                nextAction = "当前处于 DISCOVERY 阶段。如果用户回答了问题或说「继续」，调用 generateOutline。";
                break;
            case OUTLINE:
                nextAction = "当前处于 OUTLINE 阶段。如果用户说「继续」或确认大纲，调用 generatePageResearch。如果用户要修改大纲，调用 reviseOutline。";
                break;
            case RESEARCH:
                nextAction = "当前处于 RESEARCH 阶段，资料搜集已完成。如果用户说「继续」或「生成策划稿」，调用 generatePagePlans。注意：不要再调用 generatePageResearch，资料已经搜集完毕。";
                break;
            case PLANNING:
                nextAction = "当前处于 PLANNING 阶段，策划稿已生成。如果用户说「继续」或「生成设计稿」，调用 generateFinalDesign。如果用户要修改某页，调用 redesignPage。";
                break;
            case DESIGN:
                nextAction = "当前处于 DESIGN 阶段，最终设计稿已生成。用户可以要求修改某页（调用 redesignPage），或者导出。不需要再调用其他生成工具。";
                break;
            default:
                nextAction = "";
        }
        sb.append("\n## 当前阶段指引\n");
        sb.append(nextAction);
        sb.append("\n");

        sb.append("""

            ## 工作原则
            1. 用户想改大纲（如"加一个章节"、"把第3页删掉"） → 调用 reviseOutline
            2. 用户想改某页设计（如"第2页用对比布局"） → 调用 redesignPage
            3. 用户说"继续"、"下一步"、"好的" → 参考上方"当前阶段指引"调用对应工具，绝不重复调用已完成阶段的工具
            4. 如果用户刚提交了调研问卷的答案 → 调用 generateOutline
            5. 不确定当前状态时 → 调用 getSessionContext
            6. 每次调用工具后，用简洁的中文汇报结果
            7. 如果用户的请求不涉及工具调用（如闲聊、提问），直接用中文回复
            """);

        return sb.toString();
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return "处理完成";
        }
        Generation generation = response.getResults().get(0);
        if (generation.getOutput() == null || generation.getOutput().getText() == null) {
            return "处理完成";
        }
        return generation.getOutput().getText();
    }

    private List<ToolCallStep> extractToolCalls(ChatResponse response) {
        // Spring AI 1.1.2's ChatResponse doesn't directly expose tool call history
        // in the final response after ToolCallAdvisor resolves them.
        // Tool call tracking is handled by our SSE progress events instead.
        // Return empty list here — the frontend gets real-time progress via SSE.
        return List.of();
    }

    private void appendMessage(UUID sessionId, WorkflowMessageRole role,
                               com.deckgo.backend.workflow.enums.WorkflowStage stage,
                               JsonNode content, JsonNode toolCallsJson, String messageType) {
        WorkflowMessageEntity message = new WorkflowMessageEntity();
        message.setId(UUID.randomUUID());
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setStage(stage);
        message.setContentJson(content);
        message.setToolCallsJson(toolCallsJson);
        message.setMessageType(messageType);
        messageRepository.save(message);
    }

    private JsonNode textContent(String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("text", text);
        return node;
    }
}
