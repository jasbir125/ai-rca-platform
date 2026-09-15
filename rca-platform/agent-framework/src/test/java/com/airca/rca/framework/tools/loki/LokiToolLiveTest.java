package com.airca.rca.framework.tools.loki;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against the real Loki from infrastructure/docker/docker-compose.yml, which promtail
 * has shipped real order-service/payment-service container logs into.
 */
class LokiToolLiveTest {

    private final LokiTool tool = new LokiTool("http://localhost:3100");

    @Test
    void findsThePaymentTimeoutErrorsFromTheTrafficJustGenerated() {
        String result = tool.searchLogs("order-service", "PAYMENT_TIMEOUT", 10);

        assertThat(result).doesNotContain("unavailable");
        assertThat(result).contains("PAYMENT_TIMEOUT");
        assertThat(result).contains("count=");
    }

    @Test
    void returnsAllRecentLogsWhenNoFilterGiven() {
        String result = tool.searchLogs("order-service", "", 10);

        assertThat(result).doesNotContain("unavailable");
        assertThat(result).contains("distinct log line");
    }

    @Test
    void reportsNoDataForAServiceThatDoesNotExist() {
        String result = tool.searchLogs("does-not-exist-service", "", 10);

        assertThat(result).contains("No log lines found");
    }
}
