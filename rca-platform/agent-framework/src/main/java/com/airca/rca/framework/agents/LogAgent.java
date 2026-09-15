package com.airca.rca.framework.agents;

import com.airca.rca.framework.agent.Agent;
import com.airca.rca.framework.agent.AgentContext;
import com.airca.rca.framework.agent.AgentGuardrails;
import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.evidence.ConfidenceLevel;
import com.airca.rca.framework.evidence.Evidence;
import com.airca.rca.framework.evidence.EvidenceType;
import com.airca.rca.framework.tool.ToolCallBudget;
import com.airca.rca.framework.tools.loki.LokiTool;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gathers log evidence for one service (spec section 11.B): a general recent-activity
 * query plus an error-isolating query, both grouped/deduplicated by {@link LokiTool}
 * rather than returned as raw lines.
 */
@Component
public class LogAgent implements Agent {

    private static final Duration MAX_EXECUTION_TIME = Duration.ofSeconds(20);
    private static final int MAX_TOOL_CALLS = 4;
    private static final Set<String> ALLOWED_TOOLS = Set.of("searchLogs");

    private final LokiTool lokiTool;

    public LogAgent(LokiTool lokiTool) {
        this.lokiTool = lokiTool;
    }

    @Override
    public String name() {
        return "LOG_AGENT";
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

        evidence.add(query(context, "", "recent activity", windowMinutes, budget, toolsUsed));
        evidence.add(query(context, "\"level\":\"ERROR\"", "errors", windowMinutes, budget, toolsUsed));

        long duration = System.currentTimeMillis() - start;
        String summary = "Searched Loki for %s: general activity and error-level entries over the last %d minute(s)."
                .formatted(context.service(), windowMinutes);
        return AgentResult.completed(name(), duration, toolsUsed, evidence, summary);
    }

    private Evidence query(AgentContext context, String logQlFilter, String label, int windowMinutes,
            ToolCallBudget budget, List<String> toolsUsed) {
        AgentGuardrails.assertToolAllowed(this, "searchLogs");
        budget.consume("searchLogs");
        String raw = lokiTool.searchLogs(context.service(), logQlFilter, windowMinutes);
        toolsUsed.add("searchLogs");

        boolean missing = raw.contains("unavailable");
        ConfidenceLevel confidence = missing ? ConfidenceLevel.UNKNOWN : ConfidenceLevel.CONFIRMED;

        return Evidence.builder()
                .source("Loki")
                .service(context.service())
                .type(EvidenceType.LOG)
                .description("Log search (" + label + ") for " + context.service())
                .value(raw)
                .confidence(confidence)
                .correlationId(context.correlationId())
                .build();
    }
}
