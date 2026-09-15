# Tool Calling

## Mechanism

Tools are plain Spring beans with `@Tool`-annotated methods (Spring AI's tool-calling
API — see `org.springframework.ai.tool.annotation.Tool`/`@ToolParam`). Registering a
tool on a `ChatClient` call (`.tools(myTool)`) lets Spring AI handle the full protocol
against Ollama: the model requests a tool call with arguments, Spring AI invokes the
actual Java method, feeds the result back to the model, and the model continues
reasoning — verified live in `OllamaModelProviderLiveTest` and `RagLiveTest`
(`agent-framework`), which assert the tool method itself was actually invoked (via a
boolean flag flipped inside it), not just that the model's text output looks
plausible.

## The tool catalog

| Tool | Class | Backs |
|---|---|---|
| `queryMetrics` | `PrometheusTool` | Metrics Agent |
| `searchLogs` | `LokiTool` | Log Agent |
| `searchTraces`, `getTrace` | `JaegerTool` | Trace Agent |
| `searchKnowledge` | `SearchKnowledgeTool` | RAG (used directly by the orchestrator's final reasoning call, and available to register elsewhere) |

Not yet implemented (backlog, spec section 12): `getRecentDeployments`,
`getGitChanges`, `getKubernetesStatus`, `getKafkaHealth`, `getServiceDependencies`,
`getPreviousIncidents`, `createJiraIncident`, `proposeRollback` — each needs its
corresponding agent (Deployment, Git, Kubernetes, Kafka, Dependency) or integration
(Jira) to exist first.

## Why fixed contracts instead of free-form queries

`PrometheusTool.queryMetrics` takes a `metric` parameter constrained to a `PrometheusMetric`
enum (`ERROR_RATE`, `REQUEST_RATE`, `LATENCY_P95`, `LATENCY_P99`,
`JVM_HEAP_USED_BYTES`, `UP`), mapped internally to real PromQL — rather than letting
the LLM write arbitrary PromQL. Same idea for `LokiTool`/`JaegerTool`: the query shape
is fixed (service + optional filter + time window), not an open string the model
composes freely. This trades some flexibility for predictability — an LLM writing raw
PromQL is a plausible source of syntax errors and wasted tool calls; a small fixed
vocabulary is easier to guardrail (spec section 49) and easier for a human reviewer to
reason about the blast radius of.

## Guardrails (see `docs/security.md` for the full picture)

- **Per-agent allow-lists**: `AgentGuardrails.assertToolAllowed` — e.g. `LogAgent`
  literally cannot call `queryMetrics`, enforced in code, not just documented.
- **Call budgets**: `ToolCallBudget` caps how many tool invocations one agent's
  investigation can make.
- **Execution timeouts**: `GuardedAgentExecutor` bounds how long an agent (and thus its
  tool calls) can run before the investigation moves on without it.
- **Graceful degradation**: every tool catches its own HTTP/connection failures and
  returns a clearly-labeled "X unavailable" string instead of throwing — verified live
  in `LokiToolUnavailableTest` (points at a dead port, asserts no exception propagates).
- **No destructive tools exist yet.** See `docs/security.md`.

## Response formatting

Tools return formatted, deduplicated/truncated text (e.g. `LokiTool` groups identical
log lines with a count instead of repeating them, capped at 15 distinct lines;
`JaegerTool` summarizes only the slowest + error-tagged spans per trace) rather than
raw JSON dumps — spec section 46's cost-control principle ("never send 1 million logs
to the LLM") applied at the tool layer, not left for the orchestrator to clean up
after the fact.
