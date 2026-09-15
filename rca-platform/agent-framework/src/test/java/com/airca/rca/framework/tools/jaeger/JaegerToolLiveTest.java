package com.airca.rca.framework.tools.jaeger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against the real Jaeger from infrastructure/docker/docker-compose.yml, holding real
 * traces from the order-service -> payment-service calls generated for these tests.
 */
class JaegerToolLiveTest {

    private final JaegerTool tool = new JaegerTool("http://localhost:16686");

    @Test
    void findsRealTracesForOrderService() {
        String result = tool.searchTraces("order-service", 10);

        assertThat(result).doesNotContain("unavailable");
        assertThat(result).contains("Trace ");
        assertThat(result).contains("durationMs=");
    }

    @Test
    void reportsNoTracesForAServiceThatWasNeverCalled() {
        String result = tool.searchTraces("does-not-exist-service", 10);

        assertThat(result).contains("No traces found");
    }
}
