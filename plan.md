# AI RCA Platform — Plan & Progress Log

Source spec: `master_prompt.txt` (64 sections, enterprise-grade agentic RCA platform).
Full architecture rationale: see `docs/architecture.md` (written in the docs phase).

## Priority: MVP Golden Path First

Per direction mid-build, everything below is secondary until this path runs live, end to end,
against real local infrastructure (no mocks in the runtime path):

```
Order Service -> Payment Service -> timeout failure introduced
   -> Prometheus shows the error/latency spike
   -> Loki contains the timeout error logs
   -> Jaeger shows a slow Payment span
   -> Metrics/Log/Trace Agents investigate (real tool calls)
   -> RAG retrieves architecture + runbook + SLA context
   -> Ollama (qwen3.5, local) reasons over evidence + RAG context
   -> RCA generated: probable root cause, confidence, evidence, impact
```

Deferred until the golden path works: api-gateway, inventory-service, Kafka (business
events), Git/Deployment/Dependency/Kubernetes/Kafka agents, human-approval/remediation,
notification (Jira/Teams), RBAC/security hardening, evaluation framework, IaC
(Kubernetes/Helm/Terraform), GitLab CI, and the full documentation set. These are the
"enterprise" phases from the original spec and come after the MVP is proven.

**Pragmatic simplification for the MVP**: rca-api and rca-orchestrator are combined into
one Spring Boot process (`rca-platform/rca-api`) for now, using an in-process async
service instead of Kafka for the investigate flow — spec section 44 explicitly allows
"Kafka or another suitable asynchronous mechanism." The orchestration engine lives in
`agent-framework` as its own class, so splitting it into a separate Kafka-driven
`rca-orchestrator` process later (per the original architecture) is a wiring change, not
a rewrite.

**Folder structure note**: spec section 5's literal tree puts each agent/tool/rag/
guardrails/evaluation/notification/remediation concern in its own top-level folder,
implying one Maven module apiece (~20 modules). Given the modular-monolith direction
above, those instead live as Java packages inside `agent-framework` (agents, tools,
rag, guardrails — all consumers of the same Agent/Tool/AiModelProvider contracts) and
`rca-api` (evaluation, notification, remediation — invoked from REST endpoints). The
empty placeholder folders created in Phase 0 for the literal tree were removed once
this became the actual shape, rather than leaving unused directories in the repo.

## Golden Path Build Order

1. [x] Order Service + Payment Service (Java 21/Spring Boot 3, REST, OTel traces,
       Micrometer metrics, structured JSON logs, configurable timeout/latency/failure)
2. [x] Local observability stack: Postgres+pgvector, Prometheus, Loki (+Promtail or OTel
       log pipeline), Jaeger, OTel Collector, Grafana — via Docker Compose
3. [x] agent-framework: Evidence model, Tool/Agent contracts, AiModelProvider (Ollama),
       externalized prompts, InvestigationOrchestrator core
4. [x] RAG: pgvector schema + ingestion + retrieval, seeded with order/payment
       architecture doc, Payment Service runbook, Payment SLA doc
5. [x] Tools: PrometheusTool, LokiTool, JaegerTool (real HTTP clients against the compose
       stack)
6. [x] Agents: MetricsAgent, LogAgent, TraceAgent
7. [x] rca-api: incidents schema (Postgres), REST endpoints, async investigate flow,
       structured RCA JSON output with confidence/evidence/impact
8. [x] Incident trigger: `scripts/incident-simulator/simulate.sh`, live-verified
       (`payment-timeout`, `payment-high-latency`, `payment-http-500` all confirmed
       against the real running services)
9. [x] End-to-end validation: the full combined run completed successfully — see the
       2026-09-10 entry below. **MVP golden path fully closed out.**

## Backlog (post-MVP, full spec scope)

**Done, moved out of backlog** (see the dated entries below for details): Kubernetes/
Helm chart, Terraform, GitLab CI, and the documentation set (README + 13 docs, with
Mermaid diagrams in `docs/architecture.md`) — all written and validated (lint/template/
dry-run/`terraform validate`/`fmt`), none applied against a real AWS/GitLab (no
credentials in this environment).

Still open:

- api-gateway, inventory-service, Kafka business events
- Git Agent (real local-git evidence for config changes) + Deployment Agent
- Dependency Agent, Kubernetes Agent, Kafka Agent, CloudWatch tool
- `BedrockModelProvider` (untestable without AWS credentials; interface already shaped
  for it — see Phase 3)
- Split rca-orchestrator into its own Kafka-driven process
- Human approval workflow, remediation generators, RBAC/Spring Security, audit log,
  PII guardrail, adversarial prompt-injection test suite
- Notification: Teams webhook, Jira client
- Web UI: **1 of the spec's 6 screens done** (incident list + search + detail + create —
  see the 2026-09-10 entry and `docs/ui-guide.md`). Still open: agent-activity view,
  knowledge base browser, settings/config screen, a distinct dashboard/overview screen,
  auth/RBAC on the UI, and wiring it into docker-compose/Helm as a served static asset.
- **Bug found, not yet fixed**: an incident whose investigation fails (e.g. LLM call
  timeout) can get stuck showing `status: INVESTIGATING` forever with a populated
  `failureReason` — `InvestigationRunner` isn't transitioning `status` to `FAILED` on
  every failure path. Found via `43515377-24af-43e2-9dd6-306612188f15` while building the
  UI (2026-09-10 entry). Needs tracing through `InvestigationRunner`/
  `InvestigationOrchestrator`'s exception handling.
- Evaluation framework (7 known incidents), contract/E2E test suite
- AI observability (LLM latency/token/cost tracking — see `docs/observability.md`)

## Progress Log

### 2026-08-19 — Phase 0: Scaffold
- Created top-level directory tree (`services/`, `rca-platform/{agent-framework,rca-api,
  rca-orchestrator,agents/*,tools/*,rag,knowledge/*,guardrails,evaluation,notification,
  remediation}`, `ui/`, `infrastructure/{docker,kubernetes,helm,terraform}`, `docs/`,
  `scripts/`).
- Added `.gitignore`, `.env.example` (all config externalized: DB, Kafka, AI provider,
  observability URLs, git evidence provider, K8s/AWS, Jira/Teams, service ports).
- Reprioritized to the MVP golden path (this file rewritten accordingly).
- **Verified**: N/A (scaffolding only). **Next**: Order Service + Payment Service.

### 2026-08-19 — Phase 1: Order Service + Payment Service — DONE
**Implemented**
- `payment-service` (Java 21 / Spring Boot 3.3.13): `/api/payments`, simulated
  processing latency + failure rate (`ChaosState`, live-mutable via `POST /admin/chaos`,
  no redeploy needed), Micrometer counters/timer, structured JSON logs (correlationId +
  traceId/spanId via Micrometer Tracing MDC), OTLP trace export, `/actuator/prometheus`.
- `order-service`: `POST/GET /api/orders`, Postgres persistence (own `order_service`
  schema, Flyway-migrated), calls Payment Service via a JDK-`HttpClient`-backed
  `RestClient` whose connect/read timeout is live-mutable via `ChaosState`
  (`POST /admin/chaos {"paymentTimeoutMs": ...}`) — this is exactly the knob the primary
  demo scenario needs (3000ms → 500ms without a redeploy). Distinguishes
  `PAYMENT_TIMEOUT` vs `PAYMENT_CALL_FAILED` and persists the order as `FAILED` with
  that reason; correlation ID propagated to the downstream call.
- Both: correlation-ID filter, global exception handler with structured `errorType`
  responses, `Dockerfile`.

**Files**: `services/pom.xml` (reactor parent), `services/order-service/**`,
`services/payment-service/**` (~30 Java classes + resources; see repo for full list).

**Tested**
- `payment-service`: 6 JUnit tests (`PaymentProcessorTest`, `PaymentControllerTest`) —
  success/failure/validation paths, live chaos mutation. `mvn test` green.
- `order-service`: 4 JUnit tests (`OrderServiceTest`) against a real embedded WireMock
  server (Jetty-backed) — success, a **real socket-level timeout** (not simulated) via
  the JDK HttpClient, and a 500 from Payment. Postgres/Flyway/JPA is **not** covered by
  Testcontainers here: this machine's Docker Desktop only accepts API ≥1.40 and every
  Testcontainers 1.x release defaults to a client that requests 1.24, so container
  bring-up fails for infra reasons unrelated to the code (documented in
  `order-service/pom.xml`). `mvn test` green (4/4).
- **Live end-to-end** (both real jars + a real `postgres:16-alpine` container on port
  5433 — chosen because this machine already runs a native Postgres on 5432, which was
  silently intercepting `localhost:5432` connections ahead of Docker's; `.env.example`
  now defaults to 5433 for this reason):
  1. `POST /api/orders` → `201 CONFIRMED`, real row written and read back via
     `GET /api/orders`.
  2. `POST http://order-service/admin/chaos {"paymentTimeoutMs":500}` — reproduces
     "order-service v1.1" live.
  3. `POST /api/orders` again → `500 FAILED`, `failureReason=PAYMENT_TIMEOUT`,
     structured log `order_failed ... errorType=PAYMENT_TIMEOUT ... reason="Payment
     Service call timed out after 500ms"` with real `traceId`/`spanId`/`correlationId`.
  4. `/actuator/prometheus` on both services shows the real counters
     (`orders_processed_total`, `payment_processed_total`) reflecting the above.

**Known limitations**
- No automated Postgres/Flyway integration test (Testcontainers/Docker-API mismatch, see
  above) — covered by the live run above instead; revisit if a compatible Testcontainers
  release becomes available.
- `payment-service` is stateless (no DB) — matches the spec, which only requires Order
  Service to persist.
- Local `?currentSchema=order_service` + `hibernate.default_schema` is a POC choice
  (shared Postgres instance, per-service schema) instead of database-per-service.

**Next (Phase 2)**: local observability stack — Postgres+pgvector, Prometheus, Loki,
Jaeger, OTel Collector, Grafana — via Docker Compose, with order-service/payment-service
wired into it (replacing today's ad hoc `docker run` Postgres).

### 2026-08-19 — Phase 2: Local observability stack (Docker Compose) — DONE
**Implemented**
- `infrastructure/docker/docker-compose.yml`: postgres (`pgvector/pgvector:pg16`,
  extension + `order_service`/`rag` schemas via `postgres/init.sql`), prometheus
  (scrapes both services' `/actuator/prometheus`), loki + promtail (docker
  service-discovery, JSON log parsing so `service`/`level`/`environment` become real
  Loki labels — not just text), jaeger all-in-one (OTLP receiver on 4317/4318, UI on
  16686), grafana (provisioned Prometheus/Loki/Jaeger datasources + a "Services
  Overview" dashboard: request rate, error rate, P95/P99 latency, orders/payments by
  status, payment latency, JVM heap, service up/down), plus order-service and
  payment-service built from their existing Dockerfiles and wired to this stack (OTLP
  traces to Jaeger, metrics scraped by Prometheus, JSON logs shipped to Loki via
  promtail). No standalone OTel Collector for the MVP — both services export OTLP
  directly to Jaeger, which is a valid simplification for this scale; revisit if a
  collector-level processing step (sampling/filtering) is needed later.
- Deliberate choices: Postgres on host port 5433 (see Phase 1), Grafana anonymous
  Viewer access enabled for frictionless local demo viewing (admin/admin still set via
  `GRAFANA_ADMIN_PASSWORD`), Loki 2.9.8 / Promtail 2.9.8 / Jaeger 1.60 / Prometheus
  v2.54.1 / Grafana 11.1.0 pinned (all confirmed pullable from this environment).

**Files**: `infrastructure/docker/docker-compose.yml`,
`infrastructure/docker/{postgres/init.sql, prometheus/prometheus.yml,
loki/loki-config.yml, promtail/promtail-config.yml,
grafana/provisioning/{datasources,dashboards}/*.yml,
grafana/dashboards/services-overview.json}`.

**Tested — live, real containers, no mocks**
1. `mvn -DskipTests package` (both services) → `docker compose up --build -d`: all 8
   containers reach `Up`/`healthy`.
2. Confirmed live: order-service `/actuator/health` shows `db: UP` (real Postgres in
   the compose network); Prometheus `/api/v1/targets` shows both jobs `up`; Jaeger
   `/api/services` lists `order-service` and `payment-service`; Loki `/ready`; Grafana
   `/api/health` and provisioned datasources/dashboard both present via API.
3. Generated real traffic through the containerized services (3 normal orders, then
   flipped `POST order-service/admin/chaos {"paymentTimeoutMs":500}` — the primary demo
   scenario — and generated 3 more), then queried each backend directly and confirmed
   the **exact golden-path evidence chain**:
   - **Loki**: `{log_service="order-service"} |= "PAYMENT_TIMEOUT"` returns the 3 real
     `order_failed ... errorType=PAYMENT_TIMEOUT ... reason="Payment Service call timed
     out after 500ms"` log lines, each with a real `traceId`/`correlationId`.
   - **Prometheus**: `orders_processed_total{status="confirmed"} = 3`,
     `orders_processed_total{status="failed"} = 3`, matching exactly.
   - **Jaeger**: real spans show `POST /api/payments` taking ~730-760ms (matches
     Payment's simulated ~700ms latency) while the corresponding `POST /api/orders`
     spans that failed show ~504-525ms with `error=true` — i.e. Jaeger's own trace data
     independently shows the order timing out just after 500ms while Payment was still
     working (~700ms), which is the root-cause signal the RCA agents will reason over
     later.
   - **Grafana**: datasources and the "Services Overview" dashboard both provisioned
     and queryable via the API.

**Known limitations**
- No standalone OTel Collector (direct OTLP-to-Jaeger export instead) — fine for this
  scale, would add a collector if sampling/routing/multi-backend fanout is needed.
- Grafana anonymous Viewer access is enabled for local-demo convenience; not something
  to carry into any shared/deployed environment.

**Next (Phase 3)**: `agent-framework` — Evidence model, Tool/Agent contracts,
`AiModelProvider` (Ollama, using the already-pulled `qwen3.5:latest`), externalized
prompts, and the `InvestigationOrchestrator` core.

### 2026-08-19 — Phase 3: agent-framework foundations — DONE
**Implemented**
- `Evidence` (record, validated, builder) + `EvidenceType`/`ConfidenceLevel` enums —
  the standardized fact model (spec section 16).
- `RcaTool` marker + `ToolExecutionException` + `ToolCallBudget` (per-agent tool-call
  cap, spec section 46/49).
- `Agent`/`AgentContext`/`AgentResult`/`AgentStatus` contracts + `AgentGuardrails`
  (allow-list enforcement) + `GuardedAgentExecutor` (runs an agent under its declared
  timeout, converts timeout/exception into a normal `AgentResult` instead of crashing
  the investigation — spec section 27).
- `AiModelProvider` interface + `OllamaModelProvider`: chose to expose Spring AI's
  `ChatClient` directly (rather than a hand-rolled request/response type) since Spring
  AI's message/tool-calling/structured-output contract is already provider-agnostic.
  `OllamaChatModel` is autoconfigured by `spring-ai-starter-model-ollama` from
  `spring.ai.ollama.*` properties, which the consuming app sources from
  `OLLAMA_BASE_URL`/`LLM_MODEL` — nothing hardcoded here.
- `PromptLoader`, reading versioned prompt files from `resources/prompts/*.txt` (spec
  section 48) instead of Java string literals; wrote the real `system-prompt.txt`
  (evidence-only claims, confidence vocabulary, conflicting-evidence handling,
  prompt-injection defense per spec section 26/47).
- **Deferred to backlog**: `BedrockModelProvider`. The golden path is Ollama-only;
  Bedrock needs AWS credentials this environment doesn't have, so building and being
  unable to test it now would just be unverified code. The `AiModelProvider` interface
  and `ai.provider` config switch are already shaped so adding it later is additive.

**Files**: `rca-platform/pom.xml` (reactor parent, Spring AI BOM 1.1.8),
`rca-platform/agent-framework/**` (pom.xml + 12 main classes + 1 prompt file).

**Tested — 17/17 passing, including 2 live LLM tests**
- Unit: `EvidenceTest` (validation + builder), `ToolCallBudgetTest`,
  `AgentGuardrailsTest`, `GuardedAgentExecutorTest` (proves a 5-second-sleeping fake
  agent under a 150ms budget returns `TIMEOUT` in under 1s instead of hanging; proves a
  throwing agent returns `FAILED` with the real cause message instead of propagating),
  `PromptLoaderTest`.
- **Live, against the real local Ollama daemon** (`qwen3.5:latest`, no mocks):
  `OllamaModelProviderLiveTest` — (1) a plain prompt through `AiModelProvider` gets a
  real model response; (2) a real `@Tool`-annotated probe object registered on
  `chatClient().prompt().tools(probe)` is genuinely invoked by the model (verified via
  a boolean flag flipped inside the tool method, plus the model correctly extracting
  `"order-service"` as the tool argument and incorporating the tool's returned `"42%"`
  into its final answer) — confirms Spring AI's tool-calling loop actually works
  end-to-end against this model, which the entire agentic architecture depends on.
  (~131s combined — most of it real model inference/tool-call round-trips, not
  overhead.)

**Known limitations**
- `BedrockModelProvider` not yet built (see above).
- `InvestigationOrchestrator` core deferred to Phase 7/8, once agents/tools/RAG exist
  to orchestrate — building it now would be an untested shell.

**Next (Phase 4)**: RAG — pgvector schema (via Spring AI's `PgVectorStore`), Ollama
embedding client (pulling `nomic-embed-text`), ingestion pipeline, and the first real
knowledge documents (order/payment architecture, Payment runbook, Payment SLA).

### 2026-08-19 — Phase 4: RAG (retrieval-augmented knowledge) — DONE
**Implemented**
- Pulled `nomic-embed-text` (768-dim) via Ollama for embeddings, confirmed live.
- `FrontmatterParser` (+ `ParsedDocument`): parses the `---\nkey: value\n---` metadata
  header (service/environment/documentType/domain/tags — spec section 9) from a
  document's body.
- `DocumentIngestionService`: Document -> Loader -> Parser -> Chunking
  (Spring AI's `TokenTextSplitter`) -> Metadata extraction -> Embedding -> pgvector,
  exactly the pipeline in spec section 9. Loads from the filesystem (not the classpath)
  so `knowledge/` can be edited/mounted without a rebuild.
- `KnowledgeRetrievalService`: wraps Spring AI's `VectorStore.similaritySearch`, with
  metadata-filter-expression support (spec section 9's "service = X, environment = Y"
  example) and a similarity threshold so irrelevant chunks don't get retrieved just to
  fill topK.
- `SearchKnowledgeTool` (`RcaTool` + `@Tool`): the `searchKnowledge(query, metadata)`
  contract from spec section 12, exposed to the LLM via Spring AI tool calling.
- Real knowledge documents (not placeholders) in `rca-platform/knowledge/`:
  `architecture/order-payment-architecture.md`, `runbooks/payment-service-runbook.md`,
  `previous-rcas/incident-2026-06-payment-timeout.md` — the last one is a synthetic but
  realistic precedent RCA for the exact primary demo scenario, so "similar incident
  search" (spec section 19) has something real to find later.
- pgvector wiring: `spring-ai-starter-vector-store-pgvector`, schema `rag` (already
  created by `infrastructure/docker/postgres/init.sql` in Phase 2), table auto-created
  by Spring AI on first use.

**Files**: `rca-platform/agent-framework/src/main/java/.../rag/*` (6 classes),
`rca-platform/knowledge/**/*.md` (3 docs), pom.xml additions
(spring-ai-starter-vector-store-pgvector, postgresql driver).

**Tested — 22/22 passing, including 3 live RAG tests against the real stack**
- Unit: `FrontmatterParserTest` (header/body split, missing-header fallback).
- **Live**, against the real pgvector container from Phase 2 (`localhost:5433`) and
  real Ollama embeddings (`RagLiveTest`):
  1. `ingestsTheRealKnowledgeDocumentsIntoPgvector` — ingests the actual 3 knowledge
     files, asserts the pgvector row count matches the chunk count returned.
  2. `retrievesTheRunbookForAPaymentTimeoutQuery` — a real embedding-similarity search
     for "Order Service payment timeout configuration below Payment Service latency"
     returns one of the 3 real documents.
  3. `searchKnowledgeToolIsGenuinelyInvokedAndSurfacesRealDocumentContentThroughTheLlm`
     — asks the live LLM (with `searchKnowledgeTool` registered) whether a 500ms Order
     Service timeout is safe against Payment's ~700ms latency; the model calls the real
     tool, retrieves the real runbook/architecture content, and answers that the
     configuration is unsafe/too low — i.e. RAG-grounded reasoning, not a guess.
  - Independently verified via `docker exec psql`: `rag.vector_store` holds 3 rows with
    real 768-dimension embeddings (`vector_dims(embedding) = 768`) and the correct
    `documentType` metadata (`ARCHITECTURE`, `RUNBOOK`, `PREVIOUS_RCA`).
- Fixed along the way: `FrontmatterParser` was missing `@Component` (caught immediately
  by the Spring context failing to wire `DocumentIngestionService`); the pgvector
  starter being on the classpath now requires a `DataSource` for *any* Spring context in
  this module, so `OllamaModelProviderLiveTest` needed the same datasource properties
  added even though it doesn't use RAG.

**Known limitations**
- No re-ingestion/dedup strategy yet (re-ingesting the same file adds duplicate rows) —
  fine for now since ingestion is test/startup-triggered, not repeatedly invoked; will
  add an idempotent upsert-by-source-file strategy if/when this becomes a real
  `POST /api/knowledge/documents` endpoint (spec section 42) in the rca-api phase.
- `TokenTextSplitter` produced exactly 1 chunk per document (all three are short) — chunk-boundary
  behavior on longer documents is unverified until more knowledge docs are added.

**Next (Phase 5)**: Tools — `PrometheusTool`, `LokiTool`, `JaegerTool` as real `@Tool`
methods backed by real HTTP clients against the already-running observability stack.

### 2026-08-19 — Phase 5: Tools (Prometheus, Loki, Jaeger) — DONE
**Implemented**
- `PrometheusTool` (`queryMetrics`): a fixed `PrometheusMetric` enum (ERROR_RATE,
  REQUEST_RATE, LATENCY_P95, LATENCY_P99, JVM_HEAP_USED_BYTES, UP) mapped to real PromQL
  against this platform's actual metric names, rather than letting the LLM write raw
  PromQL — a deliberately narrower, more predictable tool contract.
- `LokiTool` (`searchLogs`): builds LogQL against the `log_service` label from Phase 2's
  promtail pipeline, groups identical lines with count/first-seen/last-seen instead of
  returning every line, and caps output at 15 distinct lines (spec section 46 cost
  control).
- `JaegerTool` (`searchTraces`, `getTrace`): summarizes the slowest + error-tagged spans
  per trace (capped at 5 traces / 5 spans each) instead of dumping full span trees.
- All three: real `RestClient` calls with 5s connect/read timeouts, and every failure
  path (unreachable, timeout, malformed response) caught and turned into a clearly
  labeled "X unavailable" string — spec section 27's graceful-degradation requirement,
  verified with a real dead-port test, not just a try/catch that "should" work.

**Bug caught and fixed during testing**: passing a raw PromQL/LogQL query string (which
contains literal `{...}` label selectors, e.g. `up{job="order-service"}`) into Spring's
`RestClient` via `.uri(uriBuilder -> ...queryParam("query", promQl)...)` throws
`IllegalArgumentException: Not enough variable values available to expand` — Spring's
`UriComponentsBuilder` treats any `{name}` inside an already-set query value as a URI
template variable to resolve, not literal text. Fixed by building a fully-resolved
`java.net.URI` directly with `URLEncoder`-encoded query values instead of going through
the builder's template-expansion path. This is exactly the kind of bug that only shows
up when you actually run the tool against a real backend rather than unit-testing with
mocked HTTP.

**Files**: `rca-platform/agent-framework/src/main/java/.../tools/{prometheus,loki,jaeger}/*`
(9 classes).

**Tested — 34/34 passing (all previous + 10 new), including 9 live**
- **Live, against the real Phase 2 stack** (regenerated fresh traffic first: normal
  orders, then the chaos-induced timeout scenario, so there was current data to find):
  - `PrometheusToolLiveTest`: `UP` for order-service reports `1`; `REQUEST_RATE`
    reflects the real traffic just generated; an unknown metric name is rejected before
    ever calling Prometheus.
  - `LokiToolLiveTest`: finds the real `PAYMENT_TIMEOUT` log lines with a `count=`
    grouping; unfiltered search returns real recent logs; a nonexistent service
    correctly reports no data found.
  - `JaegerToolLiveTest`: finds real traces for order-service with real
    `durationMs=` values; a nonexistent service reports no traces found.
  - `LokiToolUnavailableTest`: pointed at `localhost:1` (nothing listening) — confirmed
    no exception is thrown and the tool returns "Loki unavailable" instead.

**Known limitations**
- `queryMetrics`'s fixed metric enum only covers what this platform's services already
  emit; a genuinely general-purpose Metrics Agent tool would need either more enum
  entries or a constrained-but-flexible query builder — deferred until an agent
  actually needs a metric not yet covered.
- Trace search summarization (slowest N spans) is a simple heuristic, not full critical-path
  analysis; fine for the primary demo scenario's single-hop Order->Payment call.

**Next (Phase 6)**: Agents — `MetricsAgent`, `LogAgent`, `TraceAgent`, composing these
tools with `AiModelProvider` and the guardrails already built.

### 2026-08-19 — Phase 6: Agents (Metrics, Log, Trace) — DONE
**Implemented**
- `MetricsAgent`, `LogAgent`, `TraceAgent`: each implements `Agent`, declares its own
  allow-listed tool(s) and budgets, and calls its tool(s) **deterministically** (no LLM
  call per agent) to build `Evidence`. Architectural call worth recording: the spec
  frames "agentic" broadly, but each agent's job here is bounded tool execution with a
  known, fixed set of useful queries — there's nothing for an LLM to decide at that
  level. The actual agentic reasoning (interpreting evidence, forming hypotheses) is a
  single LLM call at the orchestrator level over all agents' combined evidence plus RAG
  context (spec section 63's "LIVE DATA + RAG CONTEXT + AGENT TOOL RESULTS + LLM
  REASONING = EVIDENCE-BASED RCA" — the reasoning happens once, at the top). This also
  matches the user's own golden-path description ("Metrics/Log/Trace Agents investigate
  (real tool calls)" as a distinct step before "Ollama reasons over evidence").
- `AgentTimeWindows`: shared helper turning an `AgentContext` window into "minutes back"
  for tools built around relative lookback.
- Each agent's `Evidence.confidence` is `CONFIRMED` for a real result and `UNKNOWN` when
  the underlying tool reports itself unavailable — so a Prometheus/Loki/Jaeger outage
  during an investigation shows up as reduced-confidence evidence, not silently missing
  or silently faked data.

**Files**: `rca-platform/agent-framework/src/main/java/.../agents/*` (4 classes).

**Tested — 34/34 passing, including 3 new live agent tests**
- `MetricsAgentLiveTest`, `LogAgentLiveTest`, `TraceAgentLiveTest`: each runs its real
  agent through the real `GuardedAgentExecutor` against order-service's real Prometheus/
  Loki/Jaeger data (fresh traffic regenerated first) — this is the full
  `Agent -> GuardedAgentExecutor -> Tool -> real backend` pipeline in one test, not
  components tested in isolation. Confirms: `MetricsAgent` returns 4 CONFIRMED metric
  Evidence items including a genuine `UP=1`; `LogAgent` returns 2 Evidence items (general
  + error-filtered); `TraceAgent` returns 1 Evidence item containing a real trace ID.

**Known limitations**
- No Dependency Agent yet (backlog) — each agent investigates exactly one service per
  call; the orchestrator (Phase 7) is responsible for knowing to also check
  payment-service when investigating order-service, using the architecture knowledge
  doc rather than a dedicated dependency-graph tool for now.
- Git/Deployment/Kubernetes/Kafka agents remain backlog per the MVP-first
  reprioritization.

**Next (Phase 7)**: rca-api — Postgres schema for incidents/evidence/hypotheses/
timeline/recommendations, REST endpoints, and the async investigation trigger that
actually wires MetricsAgent + LogAgent + TraceAgent + RAG + the LLM together into a
real RCA report.

### 2026-08-20 — Phase 7: rca-api — persistence/REST DONE, full investigate flow pending
**Implemented**
- `InvestigationOrchestrator` (in agent-framework): runs MetricsAgent/LogAgent/
  TraceAgent in parallel, retrieves RAG knowledge (unfiltered by service — the
  Payment runbook is relevant evidence for an order-service incident, so filtering
  would have hidden it), then makes exactly one structured-output LLM call
  (`ChatClient...call().entity(RcaReport.class)`) to reason over both and produce the
  spec section 15 RCA shape. `RcaReport`/`InvestigationRequest`/`InvestigationResult`
  records; `final-rca-prompt.txt` (hypothesis-driven, conflicting-evidence,
  no-root-cause, SLA-aware, similar-incident instructions per spec sections 14/19/28/29).
- `rca-api` module: 6 JPA entities + repositories (incidents, incident_evidence,
  incident_hypotheses, incident_timeline, incident_recommendations, agent_executions —
  spec section 41, schema `rca`), `IncidentService` + `InvestigationRunner` (split into
  two beans — `InvestigationTrigger` interface + impl — specifically so `@Async`
  dispatch goes through Spring's proxy instead of a same-class self-invocation, which
  `@Async` silently ignores), bounded `investigationExecutor` thread pool (cost-control
  guardrail on concurrent investigations), REST endpoints (`POST/GET /api/incidents`,
  `POST .../investigate`, `GET .../evidence|hypotheses|timeline|recommendations`,
  `GET /api/agents/status`), `KnowledgeBootstrapRunner` (auto-ingests `knowledge/` on
  startup if pgvector looks empty).

**Two real bugs found and fixed by actually running this, not just compiling it:**
1. **LLM read timeout**: Spring AI's Ollama HTTP client's default read timeout is far
   too short for a multi-minute local reasoning generation — the first live run of
   `InvestigationOrchestrator` failed with `SocketTimeoutException` mid-response. Fixed
   with `OllamaHttpClientConfig`, a `RestClient.Builder` bean with a 10-minute read
   timeout that Spring AI's autoconfiguration picks up (also fixed the same-shaped
   `num_ctx` issue: qwen3.5's default 4096-token context was too small for the
   evidence-heavy prompt and caused severe slowdown before failing outright — raised to
   8192 via `spring.ai.ollama.chat.options.num-ctx`, and evidence truncation tightened
   from 3000 to 800 chars/item as a cost-control measure).
2. **pgvector schema/search_path collision**: rca-api's datasource URL set
   `currentSchema=rca` for its own tables, which — since `currentSchema` sets the whole
   session's `search_path` — made the `public` schema (where pgvector registers its
   HNSW operator classes) invisible, so `PgVectorStore`'s index creation failed with
   "operator class vector_cosine_ops does not exist for access method hnsw" on a fresh
   boot. Fixed by setting `currentSchema=rca,public`. This didn't surface in
   agent-framework's earlier RAG tests because those never set `currentSchema` at all.
3. Also fixed: `FrontmatterParser` missing `@Component` (Phase 4, caught same session);
   `InvestigationTrigger` interface extracted after Mockito's inline mock maker refused
   to instrument a concrete class on this machine's very new JDK (26) — cleaner design
   anyway, not just a workaround.

**Tested**
- Unit (fast, no DB/LLM): `IncidentServiceTest` (4/4) — createIncident window
  defaulting, startInvestigation triggers the async runner, 404s for unknown incidents.
- **Live, real Postgres, no LLM involved**: booted the real `rca-api` jar against the
  real docker-compose Postgres. `Flyway` created the `rca` schema and all 6 tables
  cleanly. `POST /api/incidents` → `201` with a real persisted row; `GET` by id and
  list both return it correctly; unknown id → real `404`. This is genuine end-to-end
  validation of the schema and REST layer — everything except the LLM reasoning step
  itself.
- Full agent-framework (34) + rca-api (4) test suites re-run clean after these changes.

**Blocked, not skipped — environmental, not a code issue**: mid-session this machine
hit severe memory exhaustion (swap 15-16GB/16-17GB used, load average spiked to 67,
HikariCP's own housekeeper thread starved for 16 minutes) from the combination of
Docker Desktop's VM, Ollama holding an 8.7GB model, Spotlight reindexing, and an
Xcode-triggered full-home-directory `git status`, on top of everything already running
across this long session. `InvestigationOrchestratorLiveTest` and `IncidentApiLiveTest`
(the tests that exercise the actual LLM reasoning call end to end) are written and
ready but not yet run to completion — retry once load/swap pressure has cleared
(`uptime`, `sysctl vm.swapusage`) rather than assuming either the timeout fix or the
search_path fix resolved things without seeing a full green run.

**Next**: once the host is stable, run `InvestigationOrchestratorLiveTest` and
`IncidentApiLiveTest` to completion; meanwhile continuing with non-LLM work — the
incident simulator script and wiring rca-api into docker-compose.

### 2026-08-20 — Non-LLM work while the host recovers
**Implemented**
- `scripts/incident-simulator/simulate.sh`: `reset`, `payment-timeout` (primary demo
  scenario), `payment-high-latency`, `payment-http-500`, `traffic <n>`. Live-verified
  against the real running services (no LLM involved) — `payment-timeout` correctly
  flips order creation from `201` to `500`, `payment-high-latency` correctly stays
  `201` (elevated latency, not a failure — a genuinely different signature for the
  Metrics Agent to distinguish), `payment-http-500` correctly fails every order.
- `rca-api` wired into `infrastructure/docker/docker-compose.yml`: builds from a
  `rca-platform/` context (so its Dockerfile can also `COPY` the sibling `knowledge/`
  directory), `OLLAMA_BASE_URL` defaults to `host.docker.internal` since Ollama runs on
  the host, not in the compose network. Built and started for real: container reaches
  `healthy`, Flyway validates against the schema rca-api already migrated locally,
  `KnowledgeBootstrapRunner` correctly detects the already-ingested knowledge base and
  skips re-ingestion (idempotency working as designed), and a live `POST
  /api/incidents` against the containerized instance succeeds. All non-LLM paths.

**Environment note**: host load/swap have stabilized (load ~6-7, swap ~1GB free,
holding steady) but not fully recovered from the earlier spike — holding off on
re-attempting the full `InvestigationOrchestratorLiveTest`/`IncidentApiLiveTest` runs
until there's a clearer signal, per the user's direction to work non-LLM tasks while it
settles.

### 2026-08-20 — Documentation, Helm/Kubernetes, Terraform, GitLab CI (all non-LLM)
Continued non-LLM work in parallel with (and after) the second orchestrator retry
below, while the host settled.

**Documentation** (`README.md` + 13 files under `docs/`): `architecture.md` (system +
sequence + RAG-pipeline + agent-guardrail Mermaid diagrams, plus the "deliberate
deviations from spec" rationale in one place), `local-setup.md`, `incident-demo.md`
(step-by-step golden-path walkthrough with expected output at each step),
`api-documentation.md` (full REST reference), `security.md` (honest split of what's
actually enforced vs. explicitly backlog — no auth/RBAC yet, said plainly), `aws-setup.md`,
`eks-deployment.md`, `observability.md`, `tool-calling.md`, `agent-architecture.md`,
`rag-architecture.md`, `development-guide.md`, `troubleshooting.md` (real incidents
from this build — the search_path bug, the URI-braces bug, the read-timeout bug, the
Testcontainers/Docker-API-version bug, the Mockito/JDK-26 bug — not hypothetical ones).
Every doc cross-references `plan.md` rather than duplicating the reasoning.

**Helm chart** (`infrastructure/helm/rca-platform`, spec section 36): one chart, three
deployables (`order-service`, `payment-service`, `rca-api`) driven by a `services` map
in `values.yaml` rather than one template set per service — `Namespace`,
`ServiceAccount`, `ConfigMap`, `Secret` (real value required via `--set`/uncommitted
file, never a committed default), `Deployment`/`Service`/`HorizontalPodAutoscaler`/
`PodDisruptionBudget` per service, one shared `Ingress`, same-namespace-only
`NetworkPolicy` per service. Plus `values-dev.yaml`/`values-uat.yaml`/`values-prod.yaml`
overlays (spec section 53's multi-environment support). **Validated, not just
written**: `helm lint` passes; `helm template | kubectl apply --dry-run=client`
succeeds for all 19 resources; discovered this machine actually has a live local
Kubernetes cluster (Docker Desktop's built-in one) and used it for a real
`--dry-run=server` pass (genuine API-server admission validation) against a
temporary, fully-cleaned-up test namespace — all 19 resources pass server-side too.
Confirmed the dev/uat/prod overlays actually deep-merge correctly (e.g. dev's
`replicas: 1` override takes effect). The old empty `infrastructure/kubernetes/`
placeholder directory (from Phase 0 scaffold, superseded by this Helm chart) was
removed rather than left dangling.

**Terraform** (`infrastructure/terraform`, spec section 37): VPC + EKS
(`terraform-aws-modules`), ECR (one repo per image, scan-on-push, untagged-image
lifecycle policy), IRSA IAM role for `rca-api` (Bedrock/CloudWatch/Secrets Manager
permissions, no static credentials), a Secrets Manager secret resource (value
populated out-of-band, never committed). No account IDs/credentials/regions
hardcoded — `terraform.tfvars.example` provided. **Validated**: `terraform fmt -check`,
`terraform init -backend=false` (real provider/module resolution against the public
registry), `terraform validate` — all clean. Not applied against a real AWS account
(none available in this environment) — `plan`/`apply` are the next real step for
whoever has credentials.

**GitLab CI** (`.gitlab-ci.yml`, spec section 39): validate → test → build →
security-scan → docker-build → docker-push → deploy-dev → integration-test →
deploy-uat (manual) → deploy-prod (manual). `*LiveTest` classes deliberately excluded
from the CI `test` stage (they need the real compose stack; noted as a documented
choice, not an oversight, with the tradeoff explained inline). Not run against a real
GitLab instance (none available here) — the YAML is syntactically real and follows the
spec's stage list, but hasn't executed.

**Incident simulator** (`scripts/incident-simulator/simulate.sh`) and **rca-api wired
into docker-compose** were also completed in this window — see the "Non-LLM work while
the host recovers" entry above for their live-verification details.

### 2026-08-20 — Second attempt at the full live orchestrator test: also hit host contention
Load dropped to ~2-3 and a sanity-check Ollama call completed normally, so retried
`InvestigationOrchestratorLiveTest` in the background while continuing docs/IaC work.
Ingestion and agent startup were fast (1.5s boot, ~1s RAG ingestion — much healthier
than the first attempt). But partway through the LLM reasoning call, load spiked to
**115.51** (vs. 67 the first time) and the call hit the 10-minute read timeout for
real this time (`IOException: Request timed out`), with HikariCP's housekeeper again
starved for 15+ minutes.

Diagnosed the actual cause via `ps aux -r`: `mediaanalysisd` (macOS's Photos/Spotlight
media analysis daemon) at **148% CPU**, 13+ minutes accumulated, alongside another
Xcode-triggered `git ls-files` scanning the entire home directory and
`spotlightknowledged.updater`. This is macOS background media/Spotlight indexing
unrelated to anything in this project — most likely the Photos Library reprocessing
noted in the first incident. Killed the test process to stop contributing to it.

**This is now a confirmed recurring pattern** (2 of 2 full end-to-end attempts hit
severe, unrelated host contention), not code fragility — every individual piece this
final test exercises has been independently, successfully live-verified: Ollama chat +
tool-calling (Phase 3), RAG retrieval + tool-calling (Phase 4), all three agents against
real Prometheus/Loki/Jaeger (Phase 6), rca-api's full REST+persistence layer against
real Postgres (Phase 7). What's unverified is specifically the *combination* — one
orchestrator run exercising all of it back-to-back — because that run keeps landing in
a window where the host is saturated by unrelated macOS processes.

**Recommendation for whoever picks this up next**: close Photos.app / pause iCloud
Photos sync if active, check Spotlight indexing status (`mdutil -s /`), and retry
`InvestigationOrchestratorLiveTest` (or the demo in `docs/incident-demo.md`) once
`uptime`'s load average is within a few multiples of the core count and
`sysctl vm.swapusage` shows meaningful free swap — not once it merely looks better than
the worst point, since both prior attempts looked improved right before spiking again.

### 2026-09-10 — MVP golden path: full end-to-end run completed successfully — MVP DONE
Picked up the environment as left: the full docker-compose stack (postgres, prometheus,
loki+promtail, jaeger, grafana, order-service, payment-service, rca-api) was already up
and healthy from a prior session (containers `Up`, `db: UP` on both JVM services). Host
load average was 4.38 (vs. the 67/115 that killed the two prior attempts) — proceeded.

**Ran the real golden path against the live stack, no mocks, no shortcuts:**
1. `simulate.sh reset` -> `traffic 3` (3x real `201 CONFIRMED`) -> `payment-timeout`
   (`PAYMENT_CALL_TIMEOUT_MS` 3000ms -> 500ms, Payment left at its real ~700ms) ->
   `traffic 5` (5x real `500` — the misconfiguration reproducing exactly as designed).
2. Confirmed the fault landed in the real backends before investigating: Prometheus
   `orders_processed_total{status="failed"}` incremented; Loki
   `{log_service="order-service"} |= "PAYMENT_TIMEOUT"` returned real
   `order_failed ... errorType=PAYMENT_TIMEOUT ... reason="Payment Service call timed
   out after 500ms"` lines with real `traceId`/`correlationId`.
3. `POST /api/incidents` (real service/environment/severity/description) -> `201
   PENDING`. `POST /api/incidents/{id}/investigate` -> `202 INVESTIGATING`.
4. Polled `GET /api/incidents/{id}` every 15s. Reached **`COMPLETED`** in ~9 minutes
   (10:29:15 -> 10:38:16) — the LLM reasoning call is the dominant cost, consistent with
   Phase 7's ~131s-scale local-Ollama timings observed on other flows plus this being a
   more evidence-heavy prompt; no timeout, no crash, no host contention this time.

**The resulting RCA report, read back from the real API, is correct and fully
evidence-grounded** — exactly what the spec asks for (spec section 63's "LIVE DATA + RAG
CONTEXT + AGENT TOOL RESULTS + LLM REASONING = EVIDENCE-BASED RCA"):
- `probableRootCause`: correctly identifies `PAYMENT_CALL_TIMEOUT_MS` set to 500ms as
  below Payment Service's real ~700ms latency — the actual injected fault, not a guess.
- `confidence`: 0.92.
- 3 ranked hypotheses (High/Low/Very Low) — the High one cites the real Loki log text
  *and* the RAG-retrieved precedent incident `INC-2026-0614` *and* the architecture doc's
  no-retry note; the Low/Very-Low ones are correctly downgraded using the runbook's SLA
  (p99 < 1000ms) and the absence of network evidence — this is genuine hypothesis-ranking
  over real retrieved knowledge, not templated text.
- `summary` explicitly states "Payment Service itself is functioning within SLA" —
  correctly exonerating the downstream service, which is the whole point of this demo
  scenario (config bug, not a Payment failure).
- 2 recommendations: revert the timeout to 3000ms/validate against SLA, and add
  deploy-time validation to prevent the regression recurring.
- 10 real evidence rows persisted (`GET .../evidence`), each with a real `source`
  (Prometheus/Loki/Jaeger), `confidence: CONFIRMED`, and a real `correlationId` —
  including the actual raw Loki log lines (with real `traceId`s) and the
  `chaos_applied paymentCallTimeoutMs=500` audit line, i.e. the LLM's claims trace back
  to real persisted evidence exactly as the platform's design requires.
- Reset back to healthy baseline (`paymentTimeoutMs: 3000`) afterward.

**This closes the single remaining MVP item.** Every piece of the golden path
(services -> observability -> agents -> RAG -> LLM reasoning -> persisted RCA) is now
live-verified both in isolation (Phases 1-7) and combined, in one real run, against the
real stack. The MVP is running and working end to end.

**Next**: pick from the Backlog above — Web UI is the most visible next step for making
this demoable without curl; the evaluation framework would harden confidence that this
result generalizes beyond the primary scenario.

### 2026-09-10 — Minimal web UI (incident list + search + RCA detail) — DONE
User asked for "a UI where I can check all the incidents and I type and find the RCA."
Built the smallest real slice of the backlog's "Web UI (React/Vite/TS)" item that
satisfies this: one list/detail screen, not the spec's full 6. Full rationale and file
layout: `docs/ui-guide.md`.

**Implemented**
- `ui/` — React 18 + Vite + TypeScript, no router/state-library/component-library (one
  view doesn't need them). `src/api.ts` (typed fetch client against `rca-api`'s existing
  REST endpoints — no new backend endpoints needed), `src/types.ts` (mirrors the
  response records), `App.tsx` + `components/{IncidentList,IncidentDetail,
  NewIncidentForm}.tsx`, dark-theme `styles.css`.
- Incident list: polls `GET /api/incidents` every 8s, client-side search across
  service/environment/severity/description/status/summary/probableRootCause/id (no
  server-side search endpoint exists or was needed at this data volume).
- Incident detail: polls while `PENDING`/`INVESTIGATING`; once `COMPLETED` renders
  confidence, root cause, ranked hypotheses (with evidence summaries), recommendations,
  timeline, and every raw evidence item — so an RCA claim can be traced back to the real
  Prometheus/Loki/Jaeger data it came from, matching the platform's evidence-first
  design.
- New Incident modal: `POST /api/incidents` then `POST .../investigate`, selects the new
  incident so polling starts immediately.
- **Backend change required**: `rca-api` had zero CORS config (nothing needed it before
  — only `curl`/tests called it). Added `com.airca.rcaapi.config.WebConfig`
  (`WebMvcConfigurer.addCorsMappings`) allowing `GET/POST/PUT/DELETE/OPTIONS` on
  `/api/**` from an externalized origin list (`cors.allowed-origins` in
  `application.yml`, sourced from `CORS_ALLOWED_ORIGINS`, default
  `http://localhost:5173`) — same externalized-config pattern as every other
  integration point in this app. Set in `docker-compose.yml` too.

**Tested — live, not just compiled**
- `npm run build` (`tsc -b && vite build`): compiles clean, zero TS errors.
- Rebuilt `rca-api`'s jar and Docker image with the CORS change, restarted the real
  container. Confirmed live: an `OPTIONS` preflight with `Origin: http://localhost:5173`
  against the containerized `rca-api` returns
  `Access-Control-Allow-Origin: http://localhost:5173`; a real `GET /api/incidents` with
  that origin header succeeds.
- Started the real Vite dev server (`npm run dev`, port 5173) and confirmed it serves
  (`curl` → `200`) and that the real incident list (5 incidents, including the
  `payment-timeout` one from the golden-path run above) is reachable through it via the
  same CORS-enabled API calls the UI's code makes.

**Bug found while doing this (not fixed yet — see Backlog)**: incident
`43515377-24af-43e2-9dd6-306612188f15` (from 2026-09-09, one of the host-contention
incidents in the Phase 7 entries above) is stuck at `status: INVESTIGATING` despite
having a real `failureReason` populated ("LLM reasoning step failed: ... request timed
out") — `InvestigationRunner` isn't setting `status = FAILED` on every failure path, so
this incident will show "investigation in progress" forever in any client. Left as-is
pending the user's call on whether to fix now or track as backlog.

**Known limitations**
- 1 of the spec's 6 UI screens (see `docs/ui-guide.md` for the full list of what's
  deferred: agent-activity view, knowledge base browser, settings screen, a distinct
  dashboard, auth/RBAC).
- Not wired into `docker-compose.yml`/Helm as a served static asset yet — run via
  `npm run dev` for now.
- No automated UI tests (component/E2E) — verified via real build + live CORS/API checks
  instead, consistent with how the rest of this project prioritizes live verification.

**Next**: fix the `InvestigationRunner` status-transition bug (small, isolated, and now
blocking one real incident from ever showing correctly in any client); after that, pick
from the Backlog.

### 2026-09-10 — RAG made production-ready: idempotent ingestion, no more duplicate rows
User asked to fix RAG properly ("production ready... think like a senior AI
architect"), pointing at the "no re-ingestion/dedup strategy" item that had sat in
Known Limitations since Phase 4. Full design rationale and behavior:
`docs/rag-architecture.md`'s "Idempotent ingestion" section.

**Root design decision**: content-hash-based change detection via a direct `JdbcTemplate`
SQL lookup (not a similarity search) — existence/staleness checking is exact bookkeeping,
not a semantic operation, so it shouldn't inherit vector search's approximate,
threshold-dependent behavior, and it costs zero embedding calls for the common case
(nothing changed).

**Implemented** (`DocumentIngestionService`, `KnowledgeBootstrapRunner`, new
`KnowledgeController`):
- SHA-256 hash of each file's raw content, stored as chunk metadata (`contentHash`).
- Unchanged file (hash matches what's stored) → skipped, zero embedding calls.
- Changed file → old chunks deleted (`VectorStore.delete(Filter.Expression)`, filtered
  by `source`) then replaced — never additive.
- File removed from `knowledge/` → its orphaned vector rows are cleaned up at the end
  of every `ingestDirectory()` run.
- `KnowledgeBootstrapRunner` now reconciles unconditionally on every startup instead of
  skipping if the store "looked non-empty" — that old check was a real staleness bug
  (an edited/added doc was silently never ingested past the first-ever startup), not
  just a missed optimization, now that reconciliation is cheap when nothing changed.
- New `POST /api/knowledge/reingest`: operator-triggered live refresh without an app
  restart — closes the "no operational way to update knowledge" gap.

**Tested — live, against the real running stack, not just the test suite**:
1. `RagLiveTest` gained 3 new live tests (temp-directory fixtures, real pgvector/Ollama):
   unchanged-file re-ingest is a no-op with no duplicate rows; changed-file re-ingest
   replaces in place (row count stays constant, new content present, old content gone);
   a file removed from disk has its vectors cleaned up. 6/6 passing.
2. Rebuilt and restarted the real `rca-api` container: startup reconciliation log showed
   `chunks_written=0` for all 3 real knowledge docs (unchanged) — confirmed via
   `docker exec psql`: row count stayed at 3, no duplicates from the restart.
3. **Live edit-and-detect cycle against the real container**: edited
   `knowledge/runbooks/payment-service-runbook.md` (appended a marker line), rebuilt,
   restarted — log showed `rag_document_stale_chunks_removed` then a fresh
   `rag_document_ingested chunks=1` for only that file (not all 3); row count stayed at
   3 (not 4); the new content was confirmed present via `psql`. Reverted the edit,
   rebuilt again — same clean replace-in-place behavior in reverse, marker line
   confirmed gone, row count still 3.
4. `POST /api/knowledge/reingest` against the real container: `{"chunksWritten":0}` on
   an unchanged knowledge base — confirmed idempotent and safe to call anytime.

**Correction to a claim from earlier this session**: I had told the user 3 RAG evidence
entries on one incident (each 814 chars) were "duplicate chunks" — checked the actual
content before implementing anything and found they were 3 genuinely different
documents (architecture/runbook/previous-RCA) independently truncated to the same
800-char cap by `InvestigationOrchestrator`, coincidentally producing equal lengths.
Not a bug. Noted here so the record is accurate — the real gap fixed in this entry is
the ingestion-time duplication risk, not a retrieval-time duplicate-content issue.

**Known limitations (updated)**: `POST /api/knowledge/documents` (upload via API rather
than filesystem) / `POST /api/knowledge/search` (spec section 42) still not built —
`reingest` covers "pick up a filesystem change live," not document upload through the
API.

### 2026-09-10 — Structured-output parsing hardened: a full LLM generation no longer gets thrown away over one field's shape
Found live, not hypothetically: an incident investigation ran for the normal ~14
minutes (host load was calm — this was not the mediaanalysisd contention issue from
earlier today), the LLM finished reasoning, and the whole result was then discarded —
`status` went straight to `FAILED` with `failureReason: "LLM reasoning step failed:
com.fasterxml.jackson.databind.exc.MismatchedInputException: Cannot deserialize value
of type java.lang.String from Object value (token JsonToken.START_OBJECT) ... through
reference chain: RcaReport["similarIncidents"]->ArrayList[0]"`. qwen3.5 had returned
`similarIncidents` as a list of JSON objects (`{"incidentId": "...", "description":
"..."}`) instead of the plain strings `RcaReport.similarIncidents()` (a `List<String>`)
expects — the worst place for a strict-parsing failure, since the entire
investigation's cost (the dominant cost of the whole pipeline) is wasted at the very
last step.

**Fix — two layers, matching this codebase's own existing precedent for the same class
of problem** (`RcaReport.Hypothesis.probability` is already kept as a lenient `String`
rather than an enum for exactly this reason — a slightly off-format model response
shouldn't fail the whole parse):
1. `FlexibleStringDeserializer` (new, `agent-framework/orchestrator`): applied via
   `@JsonDeserialize(contentUsing = ...)` to `recommendations`, `similarIncidents`, and
   `nextActions`. A JSON string passes through unchanged; a JSON object is flattened to
   one readable line (preferring `description`/`summary`/`text`/`incidentId`/`id`/
   `title`/`name`/`action`, in that order); an object with none of those keys falls
   back to its compact JSON form rather than throwing.
2. `final-rca-prompt.txt` instruction 9 (new): explicitly states these three fields
   must be plain strings, one short sentence per item, with a concrete example format —
   cheaper to prevent the drift than to only tolerate it.

**Tested**
- `RcaReportParsingTest` (new, no LLM needed — exercises the same Jackson path
  directly against hand-written payloads shaped like the real failure): plain-string
  list parses normally; the exact observed object-list shape is tolerated and
  flattened correctly (`description` preferred over `incidentId`); an object with none
  of the preferred keys falls back to a readable compact-JSON string instead of
  crashing. 3/3 passing.
- **Live, against the real model**: rebuilt/redeployed `rca-api`, re-ran the identical
  scenario that had just failed. Completed in ~5 minutes this time (calmer host, and
  possibly a warmer model) with a correct, 0.92-confidence RCA naming the timeout
  misconfiguration. `similarIncidents` came back as
  `"INC-2026-0614 — Order creation failures after order-service deploy (same
  PAYMENT_TIMEOUT timeout misconfiguration pattern)"` — closely mirrors the example
  format added to the prompt — and no parse errors appeared in the logs.

**Known limitation**: the deserializer's fallback preferred-key list is a heuristic,
not exhaustive — an object shaped with entirely different keys still degrades
gracefully (compact JSON string) rather than losing the field, but the compact-JSON
fallback is less readable than a real flatten. Acceptable given the alternative was
crashing the whole investigation; revisit the key list if a new object shape is
observed live.