package com.airca.rca.framework.tools.prometheus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against the real Prometheus from infrastructure/docker/docker-compose.yml, scraping
 * the real order-service/payment-service containers.
 */
class PrometheusToolLiveTest {

    private final PrometheusTool tool = new PrometheusTool("http://localhost:9090");

    @Test
    void reportsOrderServiceIsUp() {
        String result = tool.queryMetrics("order-service", "UP", 1);

        assertThat(result).contains("1");
        assertThat(result).doesNotContain("unavailable");
    }

    @Test
    void reportsRequestRateAfterRealTraffic() {
        String result = tool.queryMetrics("order-service", "REQUEST_RATE", 5);

        assertThat(result).doesNotContain("unavailable");
        assertThat(result).contains("REQUEST_RATE");
    }

    @Test
    void rejectsAnUnknownMetricNameWithoutCallingPrometheus() {
        String result = tool.queryMetrics("order-service", "NOT_A_REAL_METRIC", 5);

        assertThat(result).contains("Unknown metric");
    }
}
