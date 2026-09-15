package com.airca.rca.framework.agents;

import com.airca.rca.framework.agent.Agent;
import com.airca.rca.framework.agent.AgentContext;
import com.airca.rca.framework.agent.AgentGuardrails;
import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.evidence.ConfidenceLevel;
import com.airca.rca.framework.evidence.Evidence;
import com.airca.rca.framework.evidence.EvidenceType;
import com.airca.rca.framework.tool.ToolCallBudget;
import com.airca.rca.framework.tools.prometheus.PrometheusMetric;
import com.airca.rca.framework.tools.prometheus.PrometheusTool;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gathers metric evidence for one service (spec section 11.C: error rate, request
 * rate, P95/P99 latency, service up/down). Deliberately deterministic — it calls
 * {@link PrometheusTool} directly rather than routing through an LLM tool-calling loop,
 * since there is nothing to "decide" here that a fixed set of standard queries doesn't
 * already cover, and it keeps this agent fast, cheap, and trivially testable. The LLM
 * reasoning happens once, at the orchestrator level, over the combined evidence from
 * all agents plus RAG context (spec section 63) — not once per agent.
 */
@Component
public class MetricsAgent implements Agent {

    private static final Duration MAX_EXECUTION_TIME = Duration.ofSeconds(20);
    private static final int MAX_TOOL_CALLS = 6;
    private static final Set<String> ALLOWED_TOOLS = Set.of("queryMetrics");
    private static final List<PrometheusMetric> METRICS_QUERIED = List.of(
            PrometheusMetric.UP, PrometheusMetric.ERROR_RATE, PrometheusMetric.REQUEST_RATE, PrometheusMetric.LATENCY_P95);

    private final PrometheusTool prometheusTool;

    public MetricsAgent(PrometheusTool prometheusTool) {
        this.prometheusTool = prometheusTool;
    }

    @Override
    public String name() {
        return "METRICS_AGENT";
    }

    @Override
    public Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }

    @Override
    public Duration maxExecutionTime() {
        return MAX_EXECUTION_TIME;
    }

    @Override
    public int maxToolCalls() {
        return MAX_TOOL_CALLS;
    }

    @Override
    public AgentResult investigate(AgentContext context) {
        long start = System.currentTimeMillis();
        ToolCallBudget budget = new ToolCallBudget(MAX_TOOL_CALLS);
        List<String> toolsUsed = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        int windowMinutes = AgentTimeWindows.minutes(context);

        for (PrometheusMetric metric : METRICS_QUERIED) {
            AgentGuardrails.assertToolAllowed(this, "queryMetrics");
            budget.consume("queryMetrics");
            String raw = prometheusTool.queryMetrics(context.service(), metric.name(), windowMinutes);
            toolsUsed.add("queryMetrics");
            evidence.add(toEvidence(context, metric, raw));
        }

        long duration = System.currentTimeMillis() - start;
        String summary = "Queried %d Prometheus metric(s) for %s over the last %d minute(s)."
                .formatted(evidence.size(), context.service(), windowMinutes);
        return AgentResult.completed(name(), duration, toolsUsed, evidence, summary);
    }

    private Evidence toEvidence(AgentContext context, PrometheusMetric metric, String raw) {
        boolean missing = raw.contains("unavailable") || raw.startsWith("No data");
        return Evidence.builder()
                .source("Prometheus")
                .service(context.service())
                .type(EvidenceType.METRIC)
                .description(metric.name() + " for " + context.service())
                .value(raw)
                .confidence(missing ? ConfidenceLevel.UNKNOWN : ConfidenceLevel.CONFIRMED)
                .correlationId(context.correlationId())
                .build();
    }
}
