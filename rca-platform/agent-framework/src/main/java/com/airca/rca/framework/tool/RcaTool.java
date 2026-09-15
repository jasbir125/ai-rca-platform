package com.airca.rca.framework.tool;

/**
 * Marker for a class exposing one or more Spring AI {@code @Tool}-annotated methods
 * that agents can call (searchLogs, queryMetrics, getTrace, ...). {@link #toolName()}
 * is the identifier used in an agent's allow-list (see AgentGuardrails) — it must match
 * the name Spring AI derives from the {@code @Tool} method (the method name, unless
 * overridden).
 */
public interface RcaTool {
    String toolName();
}
