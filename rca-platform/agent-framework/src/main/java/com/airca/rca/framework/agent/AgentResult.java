package com.airca.rca.framework.agent;

import com.airca.rca.framework.evidence.Evidence;

import java.util.List;

/**
 * What an agent produced, for both the RCA reasoning step and the "Agent Activity" UI
 * (spec section 45): status, timing, which tools it actually used, and the evidence it
 * gathered. {@code errorMessage} is populated only for FAILED/TIMEOUT so a partial
 * agent failure never silently looks like a clean COMPLETED result.
 */
public record AgentResult(
        String agentName,
        AgentStatus status,
        long durationMs,
        List<String> toolsUsed,
        List<Evidence> evidence,
        String summary,
        String errorMessage) {

    public static AgentResult completed(String agentName, long durationMs, List<String> toolsUsed,
            List<Evidence> evidence, String summary) {
        return new AgentResult(agentName, AgentStatus.COMPLETED, durationMs, toolsUsed, evidence, summary, null);
    }

    public static AgentResult failed(String agentName, long durationMs, String errorMessage) {
        return new AgentResult(agentName, AgentStatus.FAILED, durationMs, List.of(), List.of(), null, errorMessage);
    }

    public static AgentResult timedOut(String agentName, long durationMs, String errorMessage) {
        return new AgentResult(agentName, AgentStatus.TIMEOUT, durationMs, List.of(), List.of(), null, errorMessage);
    }
}
