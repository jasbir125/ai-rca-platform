# Agent Architecture

## The `Agent` contract

```java
public interface Agent {
    String name();
    Set<String> allowedTools();
    Duration maxExecutionTime();
    int maxToolCalls();
    AgentResult investigate(AgentContext context);
}
```

Every agent declares its own blast radius in code (allowed tools, time budget, call
budget) rather than relying on a shared default — a reviewer can see what an agent can
and can't do by reading that one class.

## The three MVP agents

- **`MetricsAgent`** — queries `UP`, `ERROR_RATE`, `REQUEST_RATE`, `LATENCY_P95` via
  `PrometheusTool` for the incident's service.
- **`LogAgent`** — two `LokiTool` queries: general recent activity, and one filtered to
  `"level":"ERROR"` to isolate errors specifically.
- **`TraceAgent`** — one `JaegerTool.searchTraces` call, summarized to slowest +
  error-tagged spans.

Backlog (spec section 11): Git, Deployment, Kubernetes, Kafka, Dependency agents —
each needs its corresponding tool/integration built first (see `plan.md`).

## Why deterministic tool calls, not an LLM loop per agent

These three agents call their tool(s) directly rather than through their own
Spring AI `ChatClient` tool-calling loop. There's a real design decision here worth
being explicit about: the spec's language is broadly "agentic," but at the level of "go
fetch the standard error-rate/request-rate/latency metrics for this service," there's
nothing for an LLM to meaningfully decide — the useful queries are already known. Using
an LLM call per agent per query would multiply cost and latency for no reasoning
benefit at that layer.

The actual agentic reasoning — interpreting evidence, forming and ranking hypotheses,
deciding what's the probable root cause — happens exactly once, in
`InvestigationOrchestrator`'s final call, over all three agents' combined evidence plus
RAG context. This mirrors spec section 63's formula (`LIVE DATA + RAG CONTEXT + AGENT
TOOL RESULTS + LLM REASONING = EVIDENCE-BASED RCA`) taken literally: the LLM REASONING
term appears once, at the end, over everything gathered before it.

## Execution: `GuardedAgentExecutor`

```java
AgentResult result = guardedAgentExecutor.execute(agent, context);
```

Runs `agent.investigate(context)` on a background thread with a timeout equal to the
agent's own `maxExecutionTime()`. A timeout returns `AgentResult` with status
`TIMEOUT`; an uncaught exception returns status `FAILED` with the real cause message.
Neither case propagates an exception to the caller — `InvestigationOrchestrator` always
gets a well-formed `AgentResult` per agent, never a crash. Verified in
`GuardedAgentExecutorTest` with a fake agent that sleeps 5s under a 150ms budget
(returns `TIMEOUT` in under 1s, not after the full sleep) and a fake agent that throws
(returns `FAILED` with the real message).

## Orchestration: `InvestigationOrchestrator`

Runs `MetricsAgent`/`LogAgent`/`TraceAgent` in parallel (`CompletableFuture`), collects
all their evidence, separately retrieves RAG knowledge (unfiltered by service — see
`docs/rag-architecture.md` for why), then makes one structured-output LLM call. See
`docs/architecture.md`'s sequence diagram for the full flow, and `docs/api-documentation.md`
for how this is triggered/polled via REST.
