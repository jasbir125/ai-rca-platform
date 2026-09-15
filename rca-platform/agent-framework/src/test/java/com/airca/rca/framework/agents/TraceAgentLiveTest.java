package com.airca.rca.framework.agents;

import com.airca.rca.framework.agent.AgentContext;
import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.agent.AgentStatus;
import com.airca.rca.framework.agent.GuardedAgentExecutor;
import com.airca.rca.framework.tools.jaeger.JaegerTool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Against the real Jaeger from infrastructure/docker/docker-compose.yml. */
class TraceAgentLiveTest {

    private final TraceAgent agent = new TraceAgent(new JaegerTool("http://localhost:16686"));
    private final GuardedAgentExecutor executor = new GuardedAgentExecutor();

    @Test
    void gathersRealTraceEvidenceThroughTheGuardedExecutor() {
        AgentContext context = new AgentContext(
                UUID.randomUUID().toString(), "order-service", "local",
                Instant.now().minusSeconds(600), Instant.now(), "corr-3", "Order failures");

        AgentResult result = executor.execute(agent, context);

        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("Jaeger");
        assertThat(result.evidence().get(0).value()).contains("Trace ");
    }
}
