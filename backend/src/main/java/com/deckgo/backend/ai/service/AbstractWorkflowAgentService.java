package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;

public abstract class AbstractWorkflowAgentService {

    private final WorkflowAgentClientFactory workflowAgentClientFactory;
    protected final DeckGoProperties properties;
    protected final ObjectMapper objectMapper;

    protected AbstractWorkflowAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper
    ) {
        this.workflowAgentClientFactory = workflowAgentClientFactory;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    protected Optional<ChatClient> client(DeckGoProperties.Agent config) {
        return workflowAgentClientFactory.create(config);
    }

    protected String asJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 agent 输入失败", exception);
        }
    }

    protected <T> T useAgentOrFallback(
        String agentName,
        DeckGoProperties.Agent config,
        AgentCall<T> aiCall,
        AgentFallback<T> fallback
    ) {
        Optional<ChatClient> chatClient = client(config);
        if (chatClient.isEmpty()) {
            return fallbackOrThrow(agentName, fallback, null);
        }

        try {
            return aiCall.execute(chatClient.get());
        } catch (Exception exception) {
            return fallbackOrThrow(agentName, fallback, exception);
        }
    }

    private <T> T fallbackOrThrow(String agentName, AgentFallback<T> fallback, Exception cause) {
        if (properties.getAi().isFallbackEnabled()) {
            return fallback.get();
        }

        if (cause == null) {
            throw new IllegalStateException(agentName + " 不可用，且 fallback 已关闭");
        }
        throw new IllegalStateException(agentName + " 调用失败，且 fallback 已关闭", cause);
    }

    @FunctionalInterface
    protected interface AgentCall<T> {
        T execute(ChatClient chatClient) throws Exception;
    }

    @FunctionalInterface
    protected interface AgentFallback<T> {
        T get();
    }
}
