# Troubleshooting

Most of this duplicates (deliberately) the troubleshooting section in
`docs/local-setup.md`, focused specifically on things that actually went wrong during
this build — real incidents, not hypothetical ones.

## `rca-api` fails to start: `operator class "vector_cosine_ops" does not exist for access method "hnsw"`

`rca-api`'s datasource URL sets `currentSchema=rca` for its own tables. Since
`currentSchema` sets the whole JDBC session's `search_path`, it hid the `public`
schema — where pgvector registers its HNSW operator classes — from that connection,
so `PgVectorStore`'s `CREATE INDEX ... USING HNSW (embedding vector_cosine_ops)`
failed to resolve the operator class. Fixed with `currentSchema=rca,public`. If a
future schema changes this again, same root cause to check for.

## Everything against Ollama is extremely slow (minutes for a two-word reply)

Check `uptime` and `sysctl vm.swapusage` before assuming it's a code problem. During
this build, host memory pressure (Docker Desktop's VM + an 8.7GB Ollama model + IDE +
several Electron apps + Spotlight reindexing + a stray `git status` scanning the whole
home directory) pushed swap to 15-16GB/16-17GB used and load average to 67, which
starved even unrelated threads (HikariCP's own housekeeper thread was unscheduled for
16 minutes). No amount of application-level timeout tuning fixes host-level thrashing —
wait for headroom, or reduce what else is running.

Separately: Spring AI's Ollama HTTP client's default read timeout is too short for a
genuine multi-minute local reasoning generation (this platform's evidence-heavy RCA
prompt). Fixed with a custom `RestClient.Builder` bean
(`OllamaHttpClientConfig`, 10-minute read timeout) that Spring AI's autoconfiguration
picks up automatically. If you see `SocketTimeoutException: Read timed out` from an
Ollama call specifically (not a general system-slowness symptom), check this is still
wired up.

## A tool-using prompt/query with `{`/`}` characters fails with "Not enough variable values available to expand"

Spring's `RestClient` (via `UriComponentsBuilder`) treats any `{name}` inside a query
parameter *value* as a URI template variable to substitute, not literal text — a
problem for PromQL (`up{job="x"}`) and LogQL (`{log_service="x"}`), both of which use
literal braces. Fixed in `PrometheusTool`/`LokiTool` by building a fully-resolved
`java.net.URI` with `URLEncoder`-encoded values directly, bypassing the builder's
template-expansion path entirely. If you add a new tool with a query language that
uses `{}`, use the same pattern.

## Testcontainers-based tests fail with a Docker API version mismatch

Not used in this project for exactly this reason: this environment's Docker Desktop
only accepts API ≥1.40, and every available Testcontainers 1.x release's docker-java
client defaults to requesting API 1.24 (confirmed by reproducing the identical 400
response via a raw `curl --unix-socket`). Testcontainers 2.x fixes this but isn't
compatible with Spring Boot 3.3's dependency management. JPA/Postgres and RAG/pgvector
integration is instead verified by running the real services against the real
`docker-compose` Postgres — see the `*LiveTest` classes and `plan.md` Phase 1/4.

## Mockito `MockitoException: Could not modify all classes` mocking a concrete class

This machine's JDK (26, very new) restricts the bytecode retransformation Mockito's
inline mock maker needs for concrete classes. Fixed by extracting an interface
(`InvestigationTrigger`) for the one place this mattered (`IncidentService`'s
dependency on `InvestigationRunner`) rather than fighting the JDK/Mockito version —
cleaner design anyway. If you hit this again, consider whether the thing you're mocking
should be an interface regardless of this JDK quirk.

## A live test can't reach the stack

`docker compose ps` from `infrastructure/docker` — everything should show `healthy` or
`Up`. `curl localhost:8090/actuator/health` (rca-api), `:9090` (Prometheus), `:3100/ready`
(Loki), `:16686/api/services` (Jaeger). Postgres is on host port **5433**, not 5432 —
see `docs/local-setup.md` for why.
