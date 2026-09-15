package com.airca.rca.framework.ai;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Decouples agents/orchestrator from a specific LLM backend (spec section 35): local
 * development uses {@link OllamaModelProvider}; production can switch to Bedrock via
 * {@code AI_PROVIDER=BEDROCK} without changing any calling code. Exposes a Spring AI
 * {@link ChatClient} rather than a hand-rolled request/response type — Spring AI's
 * message/tool-calling/structured-output contract is already provider-agnostic once a
 * {@code ChatClient} is built, so re-wrapping it would only narrow it.
 */
public interface AiModelProvider {

    String providerName();

    ChatClient chatClient();
}
