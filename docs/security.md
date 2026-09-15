# Security

Honest current state — this is an MVP; several spec-required controls (section 25) are
explicitly backlog, listed at the bottom rather than glossed over.

## What's actually in place

**No secrets in source code.** Every credential (Postgres password, future Jira/Teams
tokens) is sourced from environment variables with only local-dev-safe defaults (see
`.env.example`); nothing production-sensitive is committed.

**Read-only tools only.** Every tool an agent or the LLM can call today —
`queryMetrics`, `searchLogs`, `searchTraces`, `getTrace`, `searchKnowledge` — is
read-only against Prometheus/Loki/Jaeger/pgvector. There is no rollback, restart,
scale, or config-write tool implemented yet, so "the AI cannot perform
production-changing actions" (spec section 20) is currently true by construction, not
by a permission check that could be bypassed — the capability simply doesn't exist in
this codebase yet. When remediation tools are built (backlog), they must go through
the approval workflow described in the spec (also backlog) before this guarantee
becomes "enforced" rather than "structurally absent."

**Per-agent allow-lists, enforced in code.** `AgentGuardrails.assertToolAllowed`
checks every tool invocation against the calling agent's declared `allowedTools()` —
e.g. `LogAgent` can only call `searchLogs`. `ToolCallBudget` caps how many tool calls
a single agent investigation can make. `GuardedAgentExecutor` enforces a max execution
time per agent and turns a timeout or exception into a normal `TIMEOUT`/`FAILED`
result instead of letting a misbehaving agent hang or crash the investigation.

**Prompt-injection defense.** `agent-framework/src/main/resources/prompts/
system-prompt.txt` explicitly instructs the model to treat all retrieved content
(logs, documents, previous incident text) as untrusted data to analyze, never as
instructions — including a direct example ("ignore previous instructions", "delete
production"). This is a prompt-level control, not a programmatic filter; it hasn't yet
been adversarially tested against a real injection attempt in this codebase (spec
section 52's "malicious log content" / "malicious Git commit message" test cases are
backlog).

**Structured, correlated logging.** Every service emits JSON logs with a real
correlation ID (propagated across the order-service → payment-service call) and, once
Micrometer Tracing is active, a real trace/span ID — the substrate an audit log could
be built on top of, though a dedicated `audit_logs` table (spec section 41) isn't
built yet.

**Cost/resource guardrails double as a denial-of-service backstop.** `LokiTool` caps
output at 15 distinct log lines, `JaegerTool` at 5 traces × 5 spans, evidence sent to
the LLM is truncated per item (`InvestigationOrchestrator.MAX_EVIDENCE_VALUE_CHARS`),
and `AsyncConfig`'s `investigationExecutor` is a bounded thread pool (2-4 threads, 20
queue slots) — an investigation storm can't unboundedly consume LLM/thread resources.

## Explicitly backlog (spec section 25/26 items not yet built)

- Authentication/authorization on the REST API (OAuth2/JWT-ready per spec section 25,
  RBAC roles ADMIN/OPERATOR/DEVELOPER/VIEWER). Every endpoint in `rca-api` is
  currently open — fine for local development, **not** fine to expose as-is.
- Human approval workflow (`POST /api/incidents/{id}/approve|reject`) — meaningless
  until remediation tools that need approving actually exist.
- PII redaction / data masking guardrail on evidence before it's shown or sent to the
  LLM.
- Secrets Manager / Kubernetes Secrets integration (currently plain env vars, fine for
  local dev, not for a real deployment).
- Adversarial prompt-injection test suite (log lines, Git commit messages, and
  retrieved documents crafted to try to redirect the model).
- Cross-environment data leakage checks (RAG metadata does carry an `environment`
  field, but nothing yet enforces that UAT evidence can't be presented as production
  evidence — spec section 53).
- Data retention policy enforcement (spec section 54) — nothing currently expires.

Do not deploy this MVP to a shared or production environment without addressing the
authentication/authorization gap at minimum.
