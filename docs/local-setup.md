# Local Setup

## Prerequisites

- Java 21, Maven
- Docker + Docker Compose
- [Ollama](https://ollama.com), running locally (`ollama serve`, or the desktop app)

## 1. Pull the models

```bash
ollama pull qwen3.5           # reasoning + tool calling
ollama pull nomic-embed-text  # RAG embeddings (768-dim)
```

Any tool-calling-capable Ollama model works for `LLM_MODEL` — verify with:

```bash
curl -s http://localhost:11434/api/chat -d '{
  "model": "your-model",
  "stream": false,
  "messages": [{"role":"user","content":"call the test tool"}],
  "tools": [{"type":"function","function":{"name":"test","description":"a test tool","parameters":{"type":"object","properties":{}}}}]
}'
```

If the response includes a `tool_calls` array, it works.

## 2. Build

```bash
(cd services && mvn -DskipTests package)
(cd rca-platform && mvn -DskipTests package)
```

## 3. Bring up the stack

```bash
cd infrastructure/docker
docker compose up --build -d
docker compose ps   # everything should reach "healthy" or "Up"
```

Postgres is exposed on host port **5433**, not 5432 — deliberately, because plenty of
dev machines (including the one this was built on) already run a native/Homebrew
Postgres on 5432, which silently wins the connection over the Docker container of the
same name on `localhost`. If you don't have anything on 5432, you can still use 5433;
just don't assume 5432 is talking to this stack.

## 4. Verify

```bash
curl -s localhost:8090/actuator/health   # rca-api - db: UP means Postgres + Flyway worked
curl -s localhost:8081/actuator/health   # order-service
curl -s localhost:8082/actuator/health   # payment-service
curl -s localhost:9090/api/v1/targets | python3 -m json.tool   # Prometheus scraping both services
curl -s localhost:3100/ready              # Loki
curl -s localhost:16686/api/services      # Jaeger - should list order-service, payment-service
```

Grafana: http://localhost:3000 (anonymous viewer access; admin/admin if you need to
edit). Dashboard: "RCA Platform - Services Overview".

## Running rca-api locally instead of in Docker

Useful while iterating on rca-api itself — faster restart than a full image rebuild:

```bash
cd infrastructure/docker
docker compose up -d postgres prometheus loki promtail jaeger grafana order-service payment-service
# leave rca-api out of compose, run it directly:
cd ../../rca-platform/rca-api
OLLAMA_BASE_URL=http://localhost:11434 mvn spring-boot:run
```

Local defaults in `rca-api/src/main/resources/application.yml` already point at
`localhost:5433` (Postgres), `localhost:9090/3100/16686` (observability), and
`localhost:11434` (Ollama) — no env vars needed if you're running everything else via
`docker compose` on this machine.

## 5. Run the web UI (optional)

A minimal React/Vite/TS UI (`ui/`) covers the "find an incident, read its RCA" flow:
incident list with live client-side search, a detail view (root cause, confidence,
hypotheses, evidence, recommendations, timeline), and a form to create + trigger a new
investigation. See [`docs/ui-guide.md`](ui-guide.md) for what it covers vs. the spec's
full 6-screen UI.

```bash
cd ui
npm install
npm run dev
```

Open http://localhost:5173. It calls `rca-api` at `http://localhost:8090` by default
(override with `VITE_API_BASE_URL`, see `ui/.env.example`). `rca-api` must have
`cors.allowed-origins` including the UI's origin — already the default
(`http://localhost:5173`) in `application.yml` / `docker-compose.yml`'s
`CORS_ALLOWED_ORIGINS`; only relevant if you serve the UI from a different port/host.

## Troubleshooting

**`rca-api` fails to start with `operator class "vector_cosine_ops" does not exist for
access method "hnsw"`**: this happened once during this build — rca-api's datasource
URL set `currentSchema=rca` for its own tables, which (since `currentSchema` sets the
whole session's `search_path`) hid the `public` schema, where pgvector registers its
HNSW operator classes. Fixed by using `currentSchema=rca,public`. If you see this again
after changing schema config, check the same thing.

**Everything is extremely slow / times out** (a two-word Ollama reply takes minutes):
check `uptime` and `sysctl vm.swapusage`. If swap is nearly full and load average is
far above your core count, this is host memory pressure (Docker Desktop's VM + a
loaded Ollama model + everything else on the machine), not a bug — see `plan.md`'s
Phase 7 entry for exactly this happening during development. Close some apps or wait
for background processes (Spotlight indexing, etc.) to finish; retrying won't help
until the host itself has headroom again.

**A live `*LiveTest` test fails with `Connection refused`**: the compose stack (or
Ollama) isn't running. These tests intentionally hit the real stack, not mocks.

**Testcontainers-based tests fail with a Docker API version error**: not used in this
project — see `plan.md` Phase 1 for why (this environment's Docker Desktop only
accepts API ≥1.40; every available Testcontainers 1.x release defaults to requesting
1.24). JPA/Postgres integration is instead verified by running the real services
against the real compose Postgres.

## Running the demo scenario

See the root [`README.md`](../README.md#running-the-demo-scenario), or
[`incident-demo.md`](incident-demo.md) for the full walkthrough with expected output at
each step.
