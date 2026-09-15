# Observability

## The stack

`infrastructure/docker/docker-compose.yml` runs Prometheus, Loki+Promtail, Jaeger, and
Grafana locally. Every service (`order-service`, `payment-service`, `rca-api`) ships
structured JSON logs, Micrometer metrics, and OTLP traces the same way — see
`logback-spring.xml` and `application.yml` in each module.

## Logs

Every log line is JSON (via `logstash-logback-encoder`), with `service`,
`environment`, `version` baked in as static fields, plus per-request `correlationId`
(minted or propagated by each service's `CorrelationIdFilter`, forwarded on the
Order→Payment call) and `traceId`/`spanId` (from Micrometer Tracing, once a span is
active). Promtail ships container stdout to Loki and — because the logs are JSON —
parses `level`/`service`/`environment` out into real Loki labels (`log_level`,
`log_service`, `log_environment`) rather than leaving them buried in unstructured
text; see `infrastructure/docker/promtail/promtail-config.yml`.

Query example (the Log Agent uses the same pattern, see `LokiTool`):

```logql
{log_service="order-service"} |= "PAYMENT_TIMEOUT"
```

## Metrics

Standard Spring Boot Actuator + Micrometer metrics
(`http_server_requests_seconds_*`, `jvm_*`) plus two custom ones:
`orders_processed_total{status="confirmed"|"failed"}` and
`payment_processed_total{status="success"|"failed"}` /
`payment_processing_duration_seconds`. Scraped by Prometheus every 5s
(`infrastructure/docker/prometheus/prometheus.yml`). `PrometheusTool` queries a fixed
set of these (`ERROR_RATE`, `REQUEST_RATE`, `LATENCY_P95`, `LATENCY_P99`,
`JVM_HEAP_USED_BYTES`, `UP`) rather than accepting arbitrary PromQL from the LLM — see
`docs/tool-calling.md` for why.

## Traces

OTLP export straight to Jaeger's built-in OTLP receiver (`localhost:4318` locally,
`jaeger:4318` in-cluster) — no separate OTel Collector for this scale (see
`plan.md` Phase 2). `management.tracing.sampling.probability=1.0` locally (sample
everything, since this is a demo/dev environment, not production traffic volume).

## Grafana

Provisioned automatically (`infrastructure/docker/grafana/provisioning`): Prometheus,
Loki, and Jaeger datasources, plus a "Services Overview" dashboard (request rate,
error rate, P95/P99 latency, orders/payments by status, payment latency, JVM heap,
service up/down). Anonymous Viewer access is enabled for frictionless local demo
viewing — not something to carry into a shared environment (see `docs/security.md`).

## AI-specific observability

Not yet built as dedicated metrics (spec section 30's LLM latency/token
usage/cost/tool-call-count tracking) — currently only visible indirectly via
`AgentExecutionEntity.durationMs` per agent (exposed at `GET /api/agents/status`) and
the investigation's overall wall-clock time. Adding Micrometer timers around the
`InvestigationOrchestrator`'s LLM call specifically is a natural next step, tracked as
backlog rather than built speculatively.
