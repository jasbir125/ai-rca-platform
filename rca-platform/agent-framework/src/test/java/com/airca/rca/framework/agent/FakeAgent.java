package com.airca.rca.framework.agent;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

/** Test fixture: an Agent whose behavior is supplied by the test. */
class FakeAgent implements Agent {

    private final String name;
    private final Set<String> allowedTools;
    private final Duration maxExecutionTime;
    private final Function<AgentContext, AgentResult> behavior;

    FakeAgent(String name, Set<String> allowedTools, Duration maxExecutionTime,
            Function<AgentContext, AgentResult> behavior) {
        this.name = name;
        this.allowedTools = allowedTools;
        this.maxExecutionTime = maxExecutionTime;
        this.behavior = behavior;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<String> allowedTools() {
        return allowedTools;
    }

    @Override
    public Duration maxExecutionTime() {
        return maxExecutionTime;
    }

    @Override
    public int maxToolCalls() {
        return 5;
    }

    @Override
    public AgentResult investigate(AgentContext context) {
        return behavior.apply(context);
    }
}
