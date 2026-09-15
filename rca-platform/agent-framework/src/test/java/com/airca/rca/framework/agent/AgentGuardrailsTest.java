package com.airca.rca.framework.agent;

import com.airca.rca.framework.tool.ToolExecutionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentGuardrailsTest {

    private final Agent logAgent = new FakeAgent(
            "LOG_AGENT", Set.of("searchLogs"), Duration.ofSeconds(5), ctx -> null);

    @Test
    void allowsAWhitelistedTool() {
        assertThatCode(() -> AgentGuardrails.assertToolAllowed(logAgent, "searchLogs"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksAToolNotOnTheAllowList() {
        assertThatThrownBy(() -> AgentGuardrails.assertToolAllowed(logAgent, "proposeRollback"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("LOG_AGENT")
                .hasMessageContaining("proposeRollback");
    }
}
