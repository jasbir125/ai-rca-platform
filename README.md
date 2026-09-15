# AI-Powered Microservice RCA Platform

An agentic AI platform that investigates production incidents in a microservices
system: it gathers live evidence from Prometheus/Loki/Jaeger, retrieves relevant
enterprise knowledge (architecture docs, runbooks, SLAs, previous incidents) via RAG,
and asks a local LLM to reason over both — producing a root-cause hypothesis with
evidence, a confidence score, and impact analysis. It does not skip straight to an
answer: every claim in the output is expected to trace back to a specific piece of
evidence, and the platform says "evidence unavailable" or "root cause could not be
determined" rather than guess.

Full spec: [`master_prompt.txt`](master_prompt.txt). Build history and every decision
made along the way (including bugs found while actually running things, not just
compiling them): [`plan.md`](plan.md).

## Status

The **MVP golden path** is built and live-validated end to end, including the full
combined run (fault injection -> agents -> RAG -> LLM reasoning -> persisted RCA):

```
Order Service --calls--> Payment Service
      |                        |
      v                        v
 (timeout misconfigured)  (real ~700ms latency)
      |
      v
Prometheus shows the error/latency spike
Loki contains the real PAYMENT_TIMEOUT error logs
Jaeger shows the real slow/failed spans
      |
      v
MetricsAgent / LogAgent / TraceAgent gather this evidence via real tool calls
      |
      v
RAG retrieves the Payment runbook, architecture doc, and a precedent incident
      |
      v
A local LLM (Ollama, qwen3.5) reasons over evidence + knowledge
      |
      v
RCA report: probable root cause, confidence, hypotheses, evidence, recommendations
```

Every stage is live-tested against the real stack, both individually (see `plan.md`
Phases 1-7) and as one combined run: fault injected -> Prometheus/Loki/Jaeger show it ->
`POST /api/incidents` -> `investigate` -> polled to `COMPLETED` -> the persisted RCA
correctly names the Payment timeout misconfiguration with 0.92 confidence, ranked
hypotheses, and evidence-backed recommendations (see `plan.md`'s 2026-09-10 entry).

Everything past the golden path — Git/Kubernetes/Kafka/Dependency agents, human
approval + remediation, RBAC, Jira/Teams notifications, evaluation framework, the web
UI, Kubernetes/Helm/Terraform, GitLab CI — is scoped in the spec but intentionally
deferred; see the "Backlog" section of `plan.md`.

## Architecture

- **`services/`** — the system being observed: `order-service` and `payment-service`
  (Java 21, Spring Boot 3), independent of the RCA platform. Each exposes an
  `/admin/chaos` endpoint for live fault injection (see `scripts/incident-simulator`).
- **`rca-platform/agent-framework`** — the agent SDK: Evidence model, Agent/Tool
  contracts and guardrails, `AiModelProvider` (Ollama via Spring AI), RAG
  (pgvector-backed), the Prometheus/Loki/Jaeger tools, the three MVP agents, and
  `InvestigationOrchestrator` (the reasoning core). A library, not a deployable.
- **`rca-platform/rca-api`** — the one executable RCA service for the MVP: REST API,
  Postgres persistence, and the async investigation trigger. Architecturally shaped so
  splitting the orchestration into a separate Kafka-driven `rca-orchestrator` process
  later (per the original spec) is a wiring change, not a rewrite — see `plan.md`.
- **`infrastructure/docker`** — the full local stack: Postgres+pgvector, Prometheus,
  Loki+Promtail, Jaeger, Grafana, both sample services, and rca-api.
- **`ui/`** — a minimal React/Vite/TS web UI: browse/search incidents, view an RCA,
  trigger a new investigation. Covers 1 of the spec's 6 UI screens — see
  `docs/ui-guide.md` for what's built vs. deferred.

Why a modular monolith (2 processes) instead of the spec's literal ~20-module,
one-service-per-agent tree: see `plan.md`'s "Folder structure note" and Phase 1-7
entries — the short version is that 20 JVMs for a POC is its own kind of
over-engineering, and the package boundaries inside `agent-framework` already make each
concern (a given agent, a given tool) independently extractable later.

## Running it locally

Prerequisites: Docker, Java 21, Maven, [Ollama](https://ollama.com) running locally.

```bash
# 1. Pull the models this platform uses (qwen3.5 for reasoning, nomic-embed-text for
#    RAG embeddings). Substitute LLM_MODEL in .env if you'd rather use a different
#    tool-calling-capable model you already have.
ollama pull qwen3.5
ollama pull nomic-embed-text

# 2. Build everything
(cd services && mvn -DskipTests package)
(cd rca-platform && mvn -DskipTests package)

# 3. Bring up the full stack
cd infrastructure/docker
docker compose up --build -d

# 4. Confirm everything is healthy
docker compose ps
curl -s localhost:8090/actuator/health   # rca-api
curl -s localhost:8081/actuator/health   # order-service
curl -s localhost:8082/actuator/health   # payment-service
```

Grafana: http://localhost:3000 (anonymous viewer access enabled locally). Jaeger UI:
http://localhost:16686. Prometheus: http://localhost:9090.

```bash
# 5. (Optional) run the web UI
cd ui
npm install
npm run dev
```

Open http://localhost:5173 — browse incidents, type to search, click one for its RCA,
or create a new incident to trigger a fresh investigation. See `docs/ui-guide.md`.

## Running the demo scenario

```bash
# Generate healthy traffic
./scripts/incident-simulator/simulate.sh traffic 3

# Inject the primary demo scenario: order-service's payment call timeout drops from
# 3000ms to 500ms while Payment Service still takes ~700ms - a config misconfiguration,
# not a Payment Service failure.
./scripts/incident-simulator/simulate.sh payment-timeout
./scripts/incident-simulator/simulate.sh traffic 5   # these will fail

# Trigger an investigation
curl -s -X POST localhost:8090/api/incidents \
  -H 'Content-Type: application/json' \
  -d '{"service":"order-service","environment":"local","severity":"HIGH","description":"Order creation is failing"}'
# -> {"id": "...", "status": "PENDING", ...}

curl -s -X POST localhost:8090/api/incidents/<id>/investigate
# -> {"incidentId": "...", "status": "INVESTIGATING"}

# Poll until it's COMPLETED (or FAILED), then read the RCA
curl -s localhost:8090/api/incidents/<id>
curl -s localhost:8090/api/incidents/<id>/evidence
curl -s localhost:8090/api/incidents/<id>/hypotheses
curl -s localhost:8090/api/incidents/<id>/recommendations

# Reset back to healthy
./scripts/incident-simulator/simulate.sh reset
```

## Testing

```bash
cd rca-platform/agent-framework && mvn test   # unit + live tests against the real stack
cd rca-platform/rca-api && mvn test
cd services/order-service && mvn test
cd services/payment-service && mvn test
```

Tests named `*LiveTest` hit the real running stack (Postgres, Prometheus, Loki, Jaeger,
Ollama) rather than mocks — bring the compose stack up first. See `plan.md` for what
each phase's tests actually verified and why (several real bugs were caught this way,
not by inspection).

## Known limitations

See `plan.md`'s per-phase "Known limitations" notes for specifics. The headline ones:
no automated Testcontainers-based Postgres integration tests (this environment's Docker
Desktop rejects the API version the available Testcontainers releases default to —
compensated with live runs against the real compose stack instead); no Bedrock
provider implementation yet (untestable without AWS credentials, but the
`AiModelProvider` abstraction is shaped for it); the web UI covers 1 of the spec's 6
screens (`docs/ui-guide.md`); a known bug where a failed investigation can get stuck
showing `INVESTIGATING` instead of `FAILED` (see `plan.md`'s 2026-09-10 entry) is not
yet fixed. RAG ingestion is now idempotent (content-hash change detection, delete-then-
replace, orphan cleanup, live reconciliation via `POST /api/knowledge/reingest`) — see
`docs/rag-architecture.md`; still missing is `POST /api/knowledge/documents` for
uploading a new doc via the API instead of the filesystem.
