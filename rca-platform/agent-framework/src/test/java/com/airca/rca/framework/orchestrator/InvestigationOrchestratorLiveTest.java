package com.airca.rca.framework.orchestrator;

import com.airca.rca.framework.TestApplication;
import com.airca.rca.framework.agent.AgentStatus;
import com.airca.rca.framework.rag.DocumentIngestionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE golden-path test: real order-service/payment-service (Phase 1-2), real chaos
 * injection reproducing the primary demo scenario, real Prometheus/Loki/Jaeger evidence
 * (Phase 5-6), real RAG knowledge (Phase 4), and one real LLM reasoning call (Phase 3) —
 * all through {@link InvestigationOrchestrator}, with no mocks anywhere in the path.
 * Requires `docker compose up -d` from infrastructure/docker to already be running.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.ai.ollama.base-url=http://localhost:11434",
        "spring.ai.ollama.chat.options.model=qwen3.5:latest",
        // qwen3.5's default context window (4096) is smaller than this evidence-heavy
        // prompt needs; without raising it, generation slows to a crawl as the model
        // effectively thrashes against the window instead of failing fast.
        "spring.ai.ollama.chat.options.num-ctx=8192",
        "spring.ai.ollama.embedding.options.model=nomic-embed-text",
        "spring.ai.ollama.init.pull-model-strategy=never",
        "spring.ai.vectorstore.pgvector.schema-name=rag",
        "spring.ai.vectorstore.pgvector.initialize-schema=true",
        "spring.datasource.url=jdbc:postgresql://localhost:5433/rca_platform",
        "spring.datasource.username=rca",
        "spring.datasource.password=rca_local_dev_only",
        "observability.prometheus.url=http://localhost:9090",
        "observability.loki.url=http://localhost:3100",
        "observability.jaeger.url=http://localhost:16686"
})
class InvestigationOrchestratorLiveTest {

    private static final Path KNOWLEDGE_BASE = Path.of("..", "knowledge");
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private InvestigationOrchestrator orchestrator;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void reproduceThePrimaryDemoScenario() throws Exception {
        // healthy baseline
        post("http://localhost:8081/admin/chaos", "{\"paymentTimeoutMs\":3000}");
        post("http://localhost:8081/api/orders", "{\"itemDescription\":\"widget\",\"amount\":9.99}");

        // "order-service v1.1": drop the payment timeout below Payment's real ~700ms latency
        post("http://localhost:8081/admin/chaos", "{\"paymentTimeoutMs\":500}");
        for (int i = 0; i < 3; i++) {
            post("http://localhost:8081/api/orders", "{\"itemDescription\":\"widget\",\"amount\":9.99}");
        }
        // leave the chaos state in place; the RCA should identify it from evidence,
        // then reset it so it doesn't bleed into other tests/manual runs.
        Thread.sleep(2000); // let promtail/prometheus scrape the fresh data
        post("http://localhost:8081/admin/chaos", "{\"paymentTimeoutMs\":3000}");
    }

    private static void post(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400 && response.statusCode() != 500) {
            // 500 is the expected order-creation failure once chaos is injected
            throw new IllegalStateException("Unexpected status " + response.statusCode() + " from " + url);
        }
    }

    @Test
    void investigatesThePrimaryDemoScenarioAndIdentifiesTheRealRootCause() {
        jdbcTemplate.execute("TRUNCATE TABLE rag.vector_store");
        ingestionService.ingestDirectory(KNOWLEDGE_BASE);

        InvestigationRequest request = new InvestigationRequest(
                UUID.randomUUID().toString(),
                "order-service",
                "local",
                "HIGH",
                "Order creation is failing. Customers report checkout errors.",
                Instant.now().minus(Duration.ofMinutes(10)),
                Instant.now(),
                UUID.randomUUID().toString());

        InvestigationResult result = orchestrator.investigate(request);

        // agents actually ran and gathered real evidence
        assertThat(result.agentResults()).hasSize(3);
        assertThat(result.agentResults()).allSatisfy(r -> assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED));
        assertThat(result.agentResults().stream().flatMap(r -> r.evidence().stream())).isNotEmpty();

        // RAG actually retrieved real knowledge
        assertThat(result.ragEvidence()).isNotEmpty();

        // the LLM reasoning step actually succeeded and produced a structured report
        assertThat(result.succeeded()).as("failure reason: %s", result.failureReason()).isTrue();
        RcaReport report = result.rcaReport();
        assertThat(report).isNotNull();
        assertThat(report.confidence()).isBetween(0.0, 1.0);
        assertThat(report.hypotheses()).isNotEmpty();

        // the actual point of the whole platform: did it find the real root cause from
        // evidence, without being told the answer?
        String rootCauseLower = report.probableRootCause().toLowerCase();
        boolean identifiedTimeoutMisconfiguration =
                rootCauseLower.contains("timeout") && (rootCauseLower.contains("payment") || rootCauseLower.contains("500"));
        assertThat(identifiedTimeoutMisconfiguration)
                .as("probableRootCause should identify the Order->Payment timeout misconfiguration, was: %s",
                        report.probableRootCause())
                .isTrue();
    }
}
