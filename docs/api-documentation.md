# rca-api Reference

Base URL: `http://localhost:8090` (local) or wherever `rca-api` is deployed.

All endpoints return JSON. Errors follow `{"timestamp", "status", "errorType",
"message"}` (see `com.airca.rcaapi.error.ErrorResponse`).

## `POST /api/incidents`

Creates an incident in `PENDING` status. Does not start an investigation.

```json
// Request
{
  "service": "order-service",
  "environment": "local",
  "severity": "HIGH",
  "description": "Order creation is failing. Customers report checkout errors.",
  "startTime": "2026-08-20T06:40:00Z"   // optional; defaults to 15 minutes before now
}
```

`201 Created`, body: an `IncidentResponse` (see below).

## `GET /api/incidents`

Lists all incidents, newest first is not guaranteed — sort client-side if needed for
the MVP (no pagination yet).

## `GET /api/incidents/{id}`

```json
{
  "id": "uuid",
  "service": "order-service",
  "environment": "local",
  "severity": "HIGH",
  "description": "...",
  "status": "PENDING | INVESTIGATING | COMPLETED | FAILED | WAITING_FOR_APPROVAL",
  "windowStart": "...", "windowEnd": "...",
  "summary": null,               // populated once COMPLETED
  "probableRootCause": null,     // populated once COMPLETED
  "confidence": null,            // 0.0-1.0, populated once COMPLETED
  "requiresHumanApproval": null, // populated once COMPLETED
  "failureReason": null,         // populated only if FAILED
  "createdAt": "...", "updatedAt": "..."
}
```

`404` if the id doesn't exist.

## `POST /api/incidents/{id}/investigate`

Starts the investigation asynchronously (spec section 44 — this never blocks waiting
for the LLM). Returns immediately.

`202 Accepted`:
```json
{"incidentId": "uuid", "status": "INVESTIGATING"}
```

Poll `GET /api/incidents/{id}` until `status` is `COMPLETED` or `FAILED`. A local model
can take a few minutes for the reasoning step — see `docs/incident-demo.md`.

## `GET /api/incidents/{id}/evidence`

Every piece of evidence gathered — from agents (Prometheus/Loki/Jaeger) and RAG
(architecture/runbooks/previous incidents), interleaved.

```json
[{
  "id": "uuid", "source": "Prometheus | Loki | Jaeger | RAG",
  "service": "order-service", "type": "METRIC | LOG | TRACE | KNOWLEDGE",
  "description": "...", "value": "...",
  "confidence": "CONFIRMED | HIGHLY_PROBABLE | PROBABLE | POSSIBLE | UNKNOWN",
  "correlationId": "...", "reference": "...", "capturedAt": "..."
}]
```

`UNKNOWN` confidence means the underlying tool reported itself unavailable when this
evidence was gathered — not that the fact itself is uncertain.

## `GET /api/incidents/{id}/hypotheses`

```json
[{"id": "uuid", "description": "...", "probability": "...", "evidenceSummary": "..."}]
```

Only populated once `COMPLETED`. Reflects the LLM's hypothesis-driven reasoning (spec
section 14) — multiple hypotheses considered, not just the winning one.

## `GET /api/incidents/{id}/timeline`

```json
[{"id": "uuid", "timestamp": "...", "event": "..."}]
```

Only populated once `COMPLETED`. `timestamp` is whatever format the LLM produced
(often relative, e.g. `"18:04"`) — not guaranteed to be a strict ISO timestamp.

## `GET /api/incidents/{id}/recommendations`

```json
[{"id": "uuid", "recommendation": "..."}]
```

Recommendations only — this platform never executes them (spec section 20/21;
approval + remediation execution are backlog).

## `GET /api/agents/status`

The 50 most recent agent executions across all incidents (spec section 42/45 — the
"Agent Activity" view).

```json
[{
  "id": "uuid", "incidentId": "uuid", "agentName": "METRICS_AGENT | LOG_AGENT | TRACE_AGENT",
  "status": "COMPLETED | FAILED | TIMEOUT", "durationMs": 1234,
  "toolsUsed": "queryMetrics,queryMetrics,...", "summary": "...", "errorMessage": null,
  "createdAt": "..."
}]
```

## Not yet implemented (backlog — see `plan.md`)

`POST /api/incidents/{id}/approve|reject`, `GET /api/services`,
`GET /api/services/{service}/dependencies`, `POST /api/knowledge/documents`,
`POST /api/knowledge/search` — all deferred until the corresponding capability
(human approval, dependency graph, knowledge-management API) is built.
