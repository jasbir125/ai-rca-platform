# Development Guide

## Repository layout

```
services/                        the observed system (order-service, payment-service)
rca-platform/
  agent-framework/                library: Evidence, Agent/Tool contracts, guardrails,
                                   AiModelProvider, RAG, tools, agents, orchestrator
  rca-api/                        the one executable RCA service (REST + persistence)
  knowledge/                      RAG source documents
ui/                               (backlog)
infrastructure/
  docker/                         local stack (docker-compose)
  helm/                           Kubernetes deployment (see docs/eks-deployment.md)
  terraform/                      AWS provisioning (see docs/aws-setup.md)
scripts/incident-simulator/       fault injection CLI
docs/                             this directory
plan.md                           build history, every decision and why, per phase
```

Why 2 processes instead of ~20 Maven modules, why agents call tools deterministically,
why rca-api/rca-orchestrator are combined for now — see `docs/architecture.md`'s
"Deliberate deviations" section and `plan.md`.

## Adding a new tool

1. Create a `@Component implements RcaTool` class in
   `agent-framework/.../tools/<backend>/` with a real HTTP client (see `PrometheusTool`,
   `LokiTool`, `JaegerTool` for the pattern: `JdkClientHttpRequestFactory` with an
   explicit timeout, build the request URI as a `java.net.URI` directly rather than via
   `RestClient`'s `uriBuilder` lambda if the query string contains `{`/`}` — see
   `plan.md` Phase 5 for exactly why that matters).
2. Every failure path (connection refused, timeout, malformed response) must return a
   clearly-labeled "X unavailable" string, never throw — write a test pointing at a
   dead port (`new YourTool("http://localhost:1")`) confirming this, following
   `LokiToolUnavailableTest`.
3. Write a `*LiveTest` against the real backend if one is running in this repo's
   compose stack.

## Adding a new agent

1. Implement `Agent` (`agent-framework/.../agents/`): declare `allowedTools()`,
   `maxExecutionTime()`, `maxToolCalls()` explicitly — don't inherit a shared default.
2. Call your tool(s) deterministically and build `Evidence` from the results (see
   `MetricsAgent` for the pattern) unless the agent genuinely needs to *decide* what to
   query based on context, in which case use `AiModelProvider.chatClient()` with the
   tool registered — see `docs/agent-architecture.md` for when each approach applies.
3. Register it in `InvestigationOrchestrator`'s constructor and
   `runAgentsInParallel`.
4. Write a `*LiveTest` running it through `GuardedAgentExecutor` against the real
   backend, following `MetricsAgentLiveTest`.

## Adding a prompt

Prompts live as files in `agent-framework/src/main/resources/prompts/*.txt`, loaded via
`PromptLoader` with `{{variable}}` substitution — never as Java string literals (spec
section 48). See `system-prompt.txt` (identity, evidence-only-claims rule,
prompt-injection defense) and `final-rca-prompt.txt` (the reasoning instructions) for
the existing style.

## Testing philosophy

This codebase leans heavily on tests that hit the real local stack
(`*LiveTest` classes) rather than mocking everything, because several real bugs in this
build were only caught by actually running things — a Spring `RestClient` URI-encoding
bug with PromQL/LogQL braces, a `pgvector`/`search_path` schema collision, an
LLM-call read-timeout too short for local model latency (all documented in `plan.md`,
with the actual fix). Before adding a mock-heavy test for something that has a real
local backend available, consider whether a live test would have caught more.

Fast unit tests (no external dependency) are still the right choice for pure logic —
`AgentGuardrailsTest`, `ToolCallBudgetTest`, `FrontmatterParserTest`,
`IncidentServiceTest` (Mockito against `InvestigationTrigger`, an interface extracted
specifically so Mockito doesn't need to bytecode-instrument a concrete class — see
`plan.md` Phase 7 for why that mattered on this JDK).

## Running everything

See `docs/local-setup.md`.
