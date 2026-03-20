package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAgentClientFactory {

    private final Optional<ChatClient.Builder> chatClientBuilder;
    private final String apiKey;

    public WorkflowAgentClientFactory(
        Optional<ChatClient.Builder> chatClientBuilder,
        @Value("${spring.ai.openai.api-key:demo-key}") String apiKey
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.apiKey = apiKey;
    }

    public Optional<ChatClient> create(DeckGoProperties.Agent agent) {
        if (!agent.isEnabled() || chatClientBuilder.isEmpty() || apiKey == null || apiKey.isBlank() || "demo-key".equals(apiKey)) {
            return Optional.empty();
        }

        ChatClient.Builder builder = chatClientBuilder.get().clone();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        boolean hasCustomOption = false;

        if (agent.getModel() != null && !agent.getModel().isBlank()) {
            options.model(agent.getModel());
            hasCustomOption = true;
        }

        if (agent.getTemperature() != null) {
            options.temperature(agent.getTemperature());
            hasCustomOption = true;
        }

        if (hasCustomOption) {
            builder.defaultOptions(options.build());
        }

        return Optional.of(builder.build());
    }
}
