package com.airca.rca.framework.tool;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-investigation cap on how many tool calls a single agent may make (spec section
 * 46/49: "Maximum agent calls", "Maximum number of tool calls"). Prevents a
 * misbehaving or looping tool-calling LLM from hammering Loki/Prometheus/etc.
 * indefinitely, and bounds LLM cost.
 */
public final class ToolCallBudget {

    private final int maxCalls;
    private final AtomicInteger used = new AtomicInteger();

    public ToolCallBudget(int maxCalls) {
        if (maxCalls <= 0) {
            throw new IllegalArgumentException("maxCalls must be positive");
        }
        this.maxCalls = maxCalls;
    }

    /**
     * Records one tool invocation and throws once the budget is exhausted.
     */
    public void consume(String toolName) {
        int count = used.incrementAndGet();
        if (count > maxCalls) {
            throw new ToolExecutionException(
                    "Tool call budget exceeded (max %d) while calling '%s'".formatted(maxCalls, toolName));
        }
    }

    public int used() {
        return used.get();
    }

    public int remaining() {
        return Math.max(0, maxCalls - used.get());
    }
}
