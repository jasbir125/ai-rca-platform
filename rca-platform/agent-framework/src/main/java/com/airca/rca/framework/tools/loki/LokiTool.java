package com.airca.rca.framework.tools.loki;

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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Log Agent's window into Loki (spec section 12: {@code searchLogs(service,
 * startTime, endTime, query)}). Groups identical log lines and caps the number
 * returned (spec section 46 cost control: "never send 1 million logs to the LLM") so a
 * noisy service can't blow the LLM's context window through this tool alone.
 */
@Component
public class LokiTool implements RcaTool {

    private static final Logger log = LoggerFactory.getLogger(LokiTool.class);
    private static final int MAX_LINES_RETURNED = 15;

    private final RestClient restClient;
    private final String baseUrl;

    public LokiTool(@Value("${observability.loki.url:http://localhost:3100}") String baseUrl) {
        this.baseUrl = baseUrl;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String toolName() {
        return "searchLogs";
    }

    @Tool(name = "searchLogs",
            description = "Search recent logs for a service, optionally filtered to lines containing a substring "
                    + "(e.g. an error type). Returns deduplicated, grouped results with counts, not raw log dumps.")
    public String searchLogs(
            @ToolParam(description = "service name as it appears in Loki's log_service label, e.g. 'order-service'") String service,
            @ToolParam(description = "substring to filter for, e.g. 'PAYMENT_TIMEOUT', or empty for all logs",
                    required = false) String query,
            @ToolParam(description = "how many minutes back to search, e.g. 15") int sinceMinutes) {

        String logQl = (query == null || query.isBlank())
                ? "{log_service=\"%s\"}".formatted(service)
                : "{log_service=\"%s\"} |= `%s`".formatted(service, query.replace("`", "'"));

        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(Math.max(1, sinceMinutes)));

        try {
            String queryString = "?query=" + URLEncoder.encode(logQl, StandardCharsets.UTF_8)
                    + "&start=" + URLEncoder.encode(start.toString(), StandardCharsets.UTF_8)
                    + "&end=" + URLEncoder.encode(end.toString(), StandardCharsets.UTF_8)
                    + "&limit=1000&direction=backward";
            URI uri = URI.create(baseUrl + "/loki/api/v1/query_range" + queryString);
            LokiQueryRangeResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(LokiQueryRangeResponse.class);
            return formatResponse(service, logQl, response);
        } catch (RestClientException e) {
            log.warn("loki_tool_unavailable service={} query={} reason={}", service, query, e.getMessage());
            return "Loki unavailable: %s. Log evidence for %s could not be retrieved.".formatted(e.getMessage(), service);
        }
    }

    private String formatResponse(String service, String logQl, LokiQueryRangeResponse response) {
        if (response == null || response.data() == null || response.data().result() == null
                || response.data().result().isEmpty()) {
            return "No log lines found for %s (query: %s) in the requested window.".formatted(service, logQl);
        }

        // group identical messages so a hot error doesn't dominate the response with
        // repeats; keep first/last-seen timestamps and a count instead.
        Map<String, LineGroup> grouped = new LinkedHashMap<>();
        for (LokiQueryRangeResponse.Stream stream : response.data().result()) {
            for (List<String> entry : stream.values()) {
                if (entry.size() < 2) {
                    continue;
                }
                String timestamp = entry.get(0);
                String line = entry.get(1);
                grouped.computeIfAbsent(line, l -> new LineGroup()).record(timestamp);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(grouped.size()).append(" distinct log line(s) for ").append(service).append(":\n");
        int shown = 0;
        for (Map.Entry<String, LineGroup> entry : grouped.entrySet()) {
            if (shown++ >= MAX_LINES_RETURNED) {
                sb.append("... and ").append(grouped.size() - MAX_LINES_RETURNED).append(" more distinct line(s), truncated.\n");
                break;
            }
            LineGroup group = entry.getValue();
            sb.append("count=").append(group.count)
                    .append(" first=").append(group.first)
                    .append(" last=").append(group.last)
                    .append(" | ").append(entry.getKey()).append('\n');
        }
        return sb.toString().strip();
    }

    private static final class LineGroup {
        int count = 0;
        String first;
        String last;

        void record(String timestamp) {
            count++;
            if (first == null) {
                first = timestamp;
            }
            last = timestamp;
        }
    }
}
