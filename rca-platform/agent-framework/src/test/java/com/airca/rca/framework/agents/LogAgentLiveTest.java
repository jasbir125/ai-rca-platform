package com.airca.rca.framework.agents;

import com.airca.rca.framework.agent.AgentContext;
import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.agent.AgentStatus;
import com.airca.rca.framework.agent.GuardedAgentExecutor;
import com.airca.rca.framework.tools.loki.LokiTool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Against the real Loki from infrastructure/docker/docker-compose.yml. */
class LogAgentLiveTest {

    private final LogAgent agent = new LogAgent(new LokiTool("http://localhost:3100"));
    private final GuardedAgentExecutor executor = new GuardedAgentExecutor();

    @Test
    void gathersRealLogEvidenceThroughTheGuardedExecutor() {
        AgentContext context = new AgentContext(
                UUID.randomUUID().toString(), "order-service", "local",
                Instant.now().minusSeconds(600), Instant.now(), "corr-2", "Order failures");

        AgentResult result = executor.execute(agent, context);

        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.evidence()).allSatisfy(e -> {
            assertThat(e.source()).isEqualTo("Loki");
            assertThat(e.service()).isEqualTo("order-service");
        });
    }
}
