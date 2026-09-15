package com.airca.rca.framework.agents;

import com.airca.rca.framework.agent.AgentContext;

import java.time.Duration;

/** Turns an {@link AgentContext}'s window into "minutes back from now" for tools that
 *  take a relative lookback rather than absolute timestamps. */
final class AgentTimeWindows {

    private static final int DEFAULT_MINUTES = 15;

    private AgentTimeWindows() {
    }

    static int minutes(AgentContext context) {
        if (context.windowStart() == null || context.windowEnd() == null) {
            return DEFAULT_MINUTES;
        }
        long minutes = Duration.between(context.windowStart(), context.windowEnd()).toMinutes();
        return (int) Math.max(1, minutes);
    }
}
