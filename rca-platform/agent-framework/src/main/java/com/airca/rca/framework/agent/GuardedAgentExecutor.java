package com.airca.rca.framework.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs an {@link Agent} under its own declared time budget and turns any failure —
 * timeout, exception, tool error — into a normal {@link AgentResult} rather than an
 * exception that would abort the whole investigation (spec section 27: "Partial agent
 * failure" and "Timeout" must degrade gracefully, not crash the RCA).
 */
public class GuardedAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(GuardedAgentExecutor.class);

    private final ExecutorService executor;

    public GuardedAgentExecutor() {
        this(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "agent-executor");
            t.setDaemon(true);
            return t;
        }));
    }

    public GuardedAgentExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public AgentResult execute(Agent agent, AgentContext context) {
        long start = System.currentTimeMillis();
        Future<AgentResult> future = executor.submit(() -> agent.investigate(context));
        try {
            return future.get(agent.maxExecutionTime().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            long duration = System.currentTimeMillis() - start;
            log.warn("agent_timeout agent={} incidentId={} maxExecutionTime={}",
                    agent.name(), context.incidentId(), agent.maxExecutionTime());
            return AgentResult.timedOut(agent.name(), duration,
                    "Agent exceeded max execution time of " + agent.maxExecutionTime());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("agent_failed agent={} incidentId={} reason={}",
                    agent.name(), context.incidentId(), cause.getMessage());
            return AgentResult.failed(agent.name(), duration, cause.getMessage());
        }
    }
}
