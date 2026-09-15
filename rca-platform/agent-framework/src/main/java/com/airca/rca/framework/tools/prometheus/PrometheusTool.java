package com.airca.rca.framework.tools.prometheus;

import com.airca.rca.framework.tool.RcaTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The Metrics Agent's window into Prometheus (spec section 12: {@code
 * queryMetrics(service, metric, startTime, endTime)}). Every failure mode — Prometheus
 * unreachable, malformed response, empty result — degrades to a clearly-labeled
 * "unavailable"/"no data" string rather than throwing, per spec section 27: a tool
 * failure must reduce RCA confidence, not crash the investigation.
 */
@Component
public class PrometheusTool implements RcaTool {

    private static final Logger log = LoggerFactory.getLogger(PrometheusTool.class);

    private final RestClient restClient;
    private final String baseUrl;

    public PrometheusTool(@Value("${observability.prometheus.url:http://localhost:9090}") String baseUrl) {
        this.baseUrl = baseUrl;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String toolName() {
        return "queryMetrics";
    }

    @Tool(name = "queryMetrics",
            description = "Query a Prometheus metric for a service over a recent time window. "
                    + "metric must be one of: ERROR_RATE, REQUEST_RATE, LATENCY_P95, LATENCY_P99, "
                    + "JVM_HEAP_USED_BYTES, UP.")
    public String queryMetrics(
            @ToolParam(description = "service name as it appears in Prometheus labels, e.g. 'order-service'") String service,
            @ToolParam(description = "one of ERROR_RATE, REQUEST_RATE, LATENCY_P95, LATENCY_P99, "
                    + "JVM_HEAP_USED_BYTES, UP") String metric,
            @ToolParam(description = "how many minutes back to compute the rate/quantile over, e.g. 5") int windowMinutes) {

        PrometheusMetric metricEnum;
        try {
            metricEnum = PrometheusMetric.valueOf(metric.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown metric '%s'. Supported: %s".formatted(metric, List.of(PrometheusMetric.values()));
        }

        String promQl = metricEnum.toPromQl(service, Math.max(1, windowMinutes));

        try {
            URI uri = URI.create(baseUrl + "/api/v1/query?query=" + URLEncoder.encode(promQl, StandardCharsets.UTF_8));
            PrometheusQueryResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(PrometheusQueryResponse.class);
            return formatResponse(service, metricEnum, promQl, response);
        } catch (RestClientException e) {
            log.warn("prometheus_tool_unavailable service={} metric={} reason={}", service, metric, e.getMessage());
            return "Prometheus unavailable: %s. Metric evidence for %s/%s could not be retrieved."
                    .formatted(e.getMessage(), service, metric);
        }
    }

    private String formatResponse(String service, PrometheusMetric metric, String promQl, PrometheusQueryResponse response) {
        if (response == null || response.data() == null || response.data().result() == null
                || response.data().result().isEmpty()) {
            return "No data returned for %s/%s (query: %s). Either the service is not reporting this metric, "
                    .formatted(service, metric, promQl)
                    + "or there was no traffic in the requested window.";
        }

        StringBuilder sb = new StringBuilder();
        for (PrometheusQueryResponse.Result result : response.data().result()) {
            String value = (result.value() != null && result.value().size() > 1)
                    ? String.valueOf(result.value().get(1))
                    : "unknown";
            sb.append(service).append(' ').append(metric).append('=').append(value);
            if (result.metric() != null && !result.metric().isEmpty()) {
                sb.append(" labels=").append(result.metric());
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
