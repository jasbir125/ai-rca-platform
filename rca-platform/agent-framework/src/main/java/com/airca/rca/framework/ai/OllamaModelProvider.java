package com.airca.rca.framework.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default local/dev provider. {@code OllamaChatModel} is autoconfigured by
 * spring-ai-starter-model-ollama from {@code spring.ai.ollama.*} properties
 * (base-url, chat.options.model) — themselves sourced from {@code OLLAMA_BASE_URL} /
 * {@code LLM_MODEL} env vars by the consuming application, never hardcoded here.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "OLLAMA", matchIfMissing = true)
public class OllamaModelProvider implements AiModelProvider {

    private final ChatClient chatClient;

    public OllamaModelProvider(OllamaChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public String providerName() {
        return "OLLAMA";
    }

    @Override
    public ChatClient chatClient() {
        return chatClient;
    }
}
