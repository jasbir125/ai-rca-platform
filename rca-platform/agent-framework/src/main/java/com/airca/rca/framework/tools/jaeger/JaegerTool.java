package com.airca.rca.framework.tools.jaeger;

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

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The Trace Agent's window into Jaeger (spec section 12: {@code getTrace(traceId)},
 * {@code searchTraces(service, startTime, endTime)}). Surfaces the slowest and any
 * error spans per trace rather than dumping every span, so a single query stays within
 * a reasonable evidence size.
 */
@Component
public class JaegerTool implements RcaTool {

    private static final Logger log = LoggerFactory.getLogger(JaegerTool.class);
    private static final int MAX_TRACES = 5;
    private static final int MAX_SPANS_PER_TRACE = 5;

    private final RestClient restClient;

    public JaegerTool(@Value("${observability.jaeger.url:http://localhost:16686}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public String toolName() {
        return "searchTraces";
    }

    @Tool(name = "searchTraces",
            description = "Search recent distributed traces for a service and summarize the slowest and any "
                    + "error-tagged spans per trace.")
    public String searchTraces(
            @ToolParam(description = "service name as reported to Jaeger, e.g. 'order-service'") String service,
            @ToolParam(description = "how many minutes back to search, e.g. 15") int sinceMinutes) {

        long endMicros = Instant.now().toEpochMilli() * 1000;
        long startMicros = Instant.now().minus(Duration.ofMinutes(Math.max(1, sinceMinutes))).toEpochMilli() * 1000;

        try {
            JaegerTracesResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/traces")
                            .queryParam("service", service)
                            .queryParam("start", startMicros)
                            .queryParam("end", endMicros)
                            .queryParam("limit", MAX_TRACES)
                            .build())
                    .retrieve()
                    .body(JaegerTracesResponse.class);
            return formatTraces(service, response);
        } catch (RestClientException e) {
            log.warn("jaeger_tool_unavailable service={} reason={}", service, e.getMessage());
            return "Jaeger unavailable: %s. Trace evidence for %s could not be retrieved.".formatted(e.getMessage(), service);
        }
    }

    @Tool(name = "getTrace", description = "Fetch and summarize a single trace by its trace ID.")
    public String getTrace(@ToolParam(description = "the Jaeger trace ID") String traceId) {
        try {
            JaegerTracesResponse response = restClient.get()
                    .uri("/api/traces/{traceId}", traceId)
                    .retrieve()
                    .body(JaegerTracesResponse.class);
            return formatTraces(null, response);
        } catch (RestClientException e) {
            log.warn("jaeger_tool_unavailable traceId={} reason={}", traceId, e.getMessage());
            return "Jaeger unavailable: %s. Trace %s could not be retrieved.".formatted(e.getMessage(), traceId);
        }
    }

    private String formatTraces(String service, JaegerTracesResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            String scope = service != null ? "for " + service : "";
            return "No traces found %s in the requested window.".formatted(scope);
        }

        StringBuilder sb = new StringBuilder();
        for (JaegerTracesResponse.JaegerTrace trace : response.data()) {
            List<JaegerTracesResponse.JaegerSpan> spans = trace.spans();
            if (spans == null || spans.isEmpty()) {
                continue;
            }
            sb.append("Trace ").append(trace.traceID()).append(":\n");
            spans.stream()
                    .sorted(Comparator.comparingLong(JaegerTracesResponse.JaegerSpan::duration).reversed())
                    .limit(MAX_SPANS_PER_TRACE)
                    .forEach(span -> {
                        boolean error = hasErrorTag(span);
                        sb.append("  - ").append(span.operationName())
                                .append(" durationMs=").append(span.duration() / 1000.0)
                                .append(error ? " ERROR" : "")
                                .append('\n');
                    });
        }
        return sb.toString().strip();
    }

    private boolean hasErrorTag(JaegerTracesResponse.JaegerSpan span) {
        if (span.tags() == null) {
            return false;
        }
        return span.tags().stream()
                .anyMatch(tag -> "error".equalsIgnoreCase(tag.key()) && Boolean.parseBoolean(String.valueOf(tag.value())));
    }
}
