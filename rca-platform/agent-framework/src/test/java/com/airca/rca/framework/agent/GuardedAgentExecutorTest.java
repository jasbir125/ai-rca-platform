package com.airca.rca.framework.agent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GuardedAgentExecutorTest {

    private final GuardedAgentExecutor executor = new GuardedAgentExecutor();
    private final AgentContext context = new AgentContext(
            "incident-1", "order-service", "local", Instant.now(), Instant.now(), "corr-1", "Order failures");

    @Test
    void returnsCompletedResultWhenAgentFinishesInTime() {
        Agent agent = new FakeAgent("METRICS_AGENT", Set.of("queryMetrics"), Duration.ofSeconds(2),
                ctx -> AgentResult.completed("METRICS_AGENT", 10, List.of("queryMetrics"), List.of(), "ok"));

        AgentResult result = executor.execute(agent, context);

        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(result.agentName()).isEqualTo("METRICS_AGENT");
    }

    @Test
    void returnsTimeoutResultInsteadOfHangingWhenAgentExceedsItsBudget() {
        Agent agent = new FakeAgent("SLOW_AGENT", Set.of(), Duration.ofMillis(150), ctx -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return AgentResult.completed("SLOW_AGENT", 5000, List.of(), List.of(), "too late");
        });

        long start = System.currentTimeMillis();
        AgentResult result = executor.execute(agent, context);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.status()).isEqualTo(AgentStatus.TIMEOUT);
        assertThat(result.errorMessage()).contains("0.15S");
        // must return close to the 150ms budget, not wait out the full 5s sleep.
        assertThat(elapsed).isLessThan(1000);
    }

    @Test
    void returnsFailedResultInsteadOfPropagatingWhenAgentThrows() {
        Agent agent = new FakeAgent("BROKEN_AGENT", Set.of(), Duration.ofSeconds(2), ctx -> {
            throw new IllegalStateException("Loki unavailable");
        });

        AgentResult result = executor.execute(agent, context);

        assertThat(result.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.errorMessage()).contains("Loki unavailable");
    }
}
