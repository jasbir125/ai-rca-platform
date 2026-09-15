package com.airca.rca.framework.agents;

import com.airca.rca.framework.agent.AgentContext;
import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.agent.AgentStatus;
import com.airca.rca.framework.agent.GuardedAgentExecutor;
import com.airca.rca.framework.tools.prometheus.PrometheusTool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Against the real Prometheus from infrastructure/docker/docker-compose.yml. */
class MetricsAgentLiveTest {

    private final MetricsAgent agent = new MetricsAgent(new PrometheusTool("http://localhost:9090"));
    private final GuardedAgentExecutor executor = new GuardedAgentExecutor();

    @Test
    void gathersRealMetricEvidenceThroughTheGuardedExecutor() {
        AgentContext context = new AgentContext(
                UUID.randomUUID().toString(), "order-service", "local",
                Instant.now().minusSeconds(600), Instant.now(), "corr-1", "Order failures");

        AgentResult result = executor.execute(agent, context);

        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(result.agentName()).isEqualTo("METRICS_AGENT");
        assertThat(result.toolsUsed()).allMatch("queryMetrics"::equals);
        assertThat(result.evidence()).hasSize(4);
        assertThat(result.evidence()).allSatisfy(e -> {
            assertThat(e.source()).isEqualTo("Prometheus");
            assertThat(e.service()).isEqualTo("order-service");
        });

        boolean foundUpMetric = result.evidence().stream()
                .anyMatch(e -> e.description().startsWith("UP") && e.value().contains("1"));
        assertThat(foundUpMetric).isTrue();
    }
}
