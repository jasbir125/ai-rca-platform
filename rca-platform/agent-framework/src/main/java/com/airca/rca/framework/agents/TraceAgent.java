package com.airca.rca.framework.agents;

import com.airca.rca.framework.agent.Agent;
import com.airca.rca.framework.agent.AgentContext;
import com.airca.rca.framework.agent.AgentGuardrails;
import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.evidence.ConfidenceLevel;
import com.airca.rca.framework.evidence.Evidence;
import com.airca.rca.framework.evidence.EvidenceType;
import com.airca.rca.framework.tool.ToolCallBudget;
import com.airca.rca.framework.tools.jaeger.JaegerTool;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Gathers distributed-trace evidence for one service (spec section 11.D): recent
 * traces, summarized to their slowest and any error-tagged spans.
 */
@Component
public class TraceAgent implements Agent {

    private static final Duration MAX_EXECUTION_TIME = Duration.ofSeconds(20);
    private static final int MAX_TOOL_CALLS = 3;
    private static final Set<String> ALLOWED_TOOLS = Set.of("searchTraces", "getTrace");

    private final JaegerTool jaegerTool;

    public TraceAgent(JaegerTool jaegerTool) {
        this.jaegerTool = jaegerTool;
    }

    @Override
    public String name() {
        return "TRACE_AGENT";
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
        int windowMinutes = AgentTimeWindows.minutes(context);

        AgentGuardrails.assertToolAllowed(this, "searchTraces");
        budget.consume("searchTraces");
        String raw = jaegerTool.searchTraces(context.service(), windowMinutes);

        boolean missing = raw.contains("unavailable");
        Evidence evidence = Evidence.builder()
                .source("Jaeger")
                .service(context.service())
                .type(EvidenceType.TRACE)
                .description("Trace search for " + context.service())
                .value(raw)
                .confidence(missing ? ConfidenceLevel.UNKNOWN : ConfidenceLevel.CONFIRMED)
                .correlationId(context.correlationId())
                .build();

        long duration = System.currentTimeMillis() - start;
        String summary = "Searched Jaeger for %s traces over the last %d minute(s)."
                .formatted(context.service(), windowMinutes);
        return AgentResult.completed(name(), duration, List.of("searchTraces"), List.of(evidence), summary);
    }
}
