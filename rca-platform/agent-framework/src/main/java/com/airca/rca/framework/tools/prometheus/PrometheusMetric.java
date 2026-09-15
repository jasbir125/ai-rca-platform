package com.airca.rca.framework.tools.prometheus;

/**
 * The fixed set of metrics agents may ask for, each mapped to a real PromQL template
 * against the metric names this platform's services actually emit. A fixed enum
 * (rather than free-form PromQL from the LLM) keeps the tool predictable and avoids
 * PromQL syntax errors from the model — the guardrail is in the contract shape, not
 * just documentation.
 */
public enum PrometheusMetric {

    ERROR_RATE("sum(rate(http_server_requests_seconds_count{application=\"%s\",status=~\"5..\"}[%dm]))"),
    REQUEST_RATE("sum(rate(http_server_requests_seconds_count{application=\"%s\"}[%dm]))"),
    LATENCY_P95("histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application=\"%s\"}[%dm])) by (le))"),
    LATENCY_P99("histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application=\"%s\"}[%dm])) by (le))"),
    JVM_HEAP_USED_BYTES("sum(jvm_memory_used_bytes{application=\"%s\",area=\"heap\"})"),
    UP("up{job=\"%s\"}");

    private final String promQlTemplate;

    PrometheusMetric(String promQlTemplate) {
        this.promQlTemplate = promQlTemplate;
    }

    public String toPromQl(String service, int windowMinutes) {
        return promQlTemplate.contains("%d")
                ? promQlTemplate.formatted(service, windowMinutes)
                : promQlTemplate.formatted(service);
    }
}
