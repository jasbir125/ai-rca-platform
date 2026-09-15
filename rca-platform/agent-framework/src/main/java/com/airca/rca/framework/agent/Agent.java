package com.airca.rca.framework.agent;

import java.time.Duration;
import java.util.Set;

/**
 * A specialized investigator (Log Agent, Metrics Agent, Trace Agent, ...). Each
 * implementation declares its own guardrails (spec section 49) rather than relying on
 * a shared default, so a reviewer can see an agent's blast radius from its own code.
 */
public interface Agent {

    String name();

    /** Tool names this agent is permitted to call; enforced by {@link AgentGuardrails}. */
    Set<String> allowedTools();

    Duration maxExecutionTime();

    int maxToolCalls();

    AgentResult investigate(AgentContext context);
}
