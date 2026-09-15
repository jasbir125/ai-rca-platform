package com.airca.rca.framework.orchestrator;

import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.evidence.Evidence;

import java.util.List;

/**
 * Everything one investigation produced: what each agent found (for the "Agent
 * Activity" UI, spec section 45), the RAG context that was retrieved, and — only when
 * the LLM reasoning step itself succeeded — the final report. {@code rcaReport} is
 * null when {@code succeeded} is false, e.g. the LLM was unavailable or its response
 * failed structured-output parsing (spec section 27/29): the platform must say "root
 * cause could not be determined" rather than fabricate one.
 */
public record InvestigationResult(
        List<AgentResult> agentResults,
        List<Evidence> ragEvidence,
        RcaReport rcaReport,
        boolean succeeded,
        String failureReason) {

    public static InvestigationResult success(List<AgentResult> agentResults, List<Evidence> ragEvidence, RcaReport rcaReport) {
        return new InvestigationResult(agentResults, ragEvidence, rcaReport, true, null);
    }

    public static InvestigationResult failure(List<AgentResult> agentResults, List<Evidence> ragEvidence, String reason) {
        return new InvestigationResult(agentResults, ragEvidence, null, false, reason);
    }
}
