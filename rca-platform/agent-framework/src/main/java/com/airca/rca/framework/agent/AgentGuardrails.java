package com.airca.rca.framework.agent;

import com.airca.rca.framework.tool.ToolExecutionException;

/**
 * Enforces the per-agent tool allow-list (spec section 49). Example: the Log Agent may
 * call {@code searchLogs} but not {@code proposeRollback} or any Kubernetes
 * delete/scale operation — this is what makes that boundary real rather than a comment.
 */
public final class AgentGuardrails {

    private AgentGuardrails() {
    }

    public static void assertToolAllowed(Agent agent, String toolName) {
        if (!agent.allowedTools().contains(toolName)) {
            throw new ToolExecutionException(
                    "Agent '%s' is not permitted to call tool '%s'. Allowed tools: %s"
                            .formatted(agent.name(), toolName, agent.allowedTools()));
        }
    }
}
