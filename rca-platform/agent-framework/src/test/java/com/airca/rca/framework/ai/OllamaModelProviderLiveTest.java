package com.airca.rca.framework.ai;

import com.airca.rca.framework.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises AiModelProvider against the real local Ollama daemon (qwen3.5, already
 * pulled on this machine) — not a mock. Confirms both plain chat and Spring AI's tool
 * calling loop actually work through this abstraction, since the whole agentic
 * architecture depends on tool calling being real, not simulated.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.ai.ollama.base-url=http://localhost:11434",
        "spring.ai.ollama.chat.options.model=qwen3.5:latest",
        "spring.ai.ollama.init.pull-model-strategy=never",
        // Not used by this test, but the pgvector starter now on the classpath (for
        // RagLiveTest) makes Spring Boot require a real datasource to build its
        // context at all, even for tests that never touch RAG.
        "spring.datasource.url=jdbc:postgresql://localhost:5433/rca_platform",
        "spring.datasource.username=rca",
        "spring.datasource.password=rca_local_dev_only",
        "spring.ai.vectorstore.pgvector.initialize-schema=false"
})
class OllamaModelProviderLiveTest {

    @Autowired
    private AiModelProvider aiModelProvider;

    @Test
    void respondsToASimplePromptThroughTheProviderAbstraction() {
        String response = aiModelProvider.chatClient()
                .prompt()
                .user("Reply with exactly the single word PONG and nothing else.")
                .call()
                .content();

        assertThat(response).containsIgnoringCase("PONG");
        assertThat(aiModelProvider.providerName()).isEqualTo("OLLAMA");
    }

    @Test
    void actuallyInvokesARealToolRatherThanHallucinatingAnAnswer() {
        ErrorRateProbe probe = new ErrorRateProbe();

        String response = aiModelProvider.chatClient()
                .prompt()
                .user("What is the current HTTP error rate for order-service? Use the tool to find out, "
                        + "then state the number you got back.")
                .tools(probe)
                .call()
                .content();

        assertThat(probe.invoked).isTrue();
        assertThat(probe.requestedService).isEqualToIgnoringCase("order-service");
        assertThat(response).contains("42");
    }

    static class ErrorRateProbe {
        boolean invoked = false;
        String requestedService;

        @Tool(description = "Get the current HTTP error rate percentage for a service")
        String getErrorRate(String service) {
            invoked = true;
            requestedService = service;
            return "42%";
        }
    }
}
