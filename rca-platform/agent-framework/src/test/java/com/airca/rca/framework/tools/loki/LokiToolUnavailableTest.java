package com.airca.rca.framework.tools.loki;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Spec section 27: "If Loki is unavailable ... Do not fabricate log information."
 * Points at a port nothing is listening on and confirms the tool degrades to a clear
 * "unavailable" message instead of throwing (which would otherwise abort the agent).
 */
class LokiToolUnavailableTest {

    private final LokiTool tool = new LokiTool("http://localhost:1");

    @Test
    void returnsAnUnavailableMessageInsteadOfThrowing() {
        assertThatCode(() -> {
            String result = tool.searchLogs("order-service", "", 5);
            assertThat(result).contains("Loki unavailable");
        }).doesNotThrowAnyException();
    }
}
