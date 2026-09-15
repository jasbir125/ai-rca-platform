# Incident Demo Walkthrough

The primary demo scenario (spec section 2): order-service normally calls Payment
Service with a 3000ms timeout; Payment Service's real latency is ~700ms. A bad
deployment (simulated here via the chaos endpoint rather than an actual redeploy — see
`plan.md` Phase 9/backlog for the git-commit-backed version) drops the timeout to
500ms. Every order now fails, even though Payment Service is healthy. The platform
should figure this out from evidence — the root cause is never hardcoded anywhere in
the reasoning path.

Prerequisites: the full stack running (`docs/local-setup.md`).

## 1. Confirm healthy baseline

```bash
./scripts/incident-simulator/simulate.sh reset
./scripts/incident-simulator/simulate.sh traffic 3
```

Expected: all three `HTTP 201`. Check Grafana's "Services Overview" dashboard —
request rate should show a small bump, error rate flat at zero.

## 2. Inject the incident

```bash
./scripts/incident-simulator/simulate.sh payment-timeout
```

This calls `POST order-service/admin/chaos {"paymentTimeoutMs":500}` — live, no
redeploy, exactly mirroring what a bad config-template change would do in production.

## 3. Generate failing traffic

```bash
./scripts/incident-simulator/simulate.sh traffic 5
```

Expected: all five `HTTP 500`. This is the "customers report checkout errors" moment.

## 4. Confirm the raw evidence exists (before AI touches it)

```bash
# Prometheus: order failure counter incremented
curl -s 'localhost:9090/api/v1/query?query=orders_processed_total' | python3 -m json.tool

# Loki: real PAYMENT_TIMEOUT error logs
curl -s -G 'localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={log_service="order-service"} |= "PAYMENT_TIMEOUT"' \
  --data-urlencode "start=$(date -u -v-10M +%Y-%m-%dT%H:%M:%SZ)" \
  --data-urlencode "end=$(date -u +%Y-%m-%dT%H:%M:%SZ)" | python3 -m json.tool

# Jaeger: real slow/failed spans — order calls cut off around 500ms while payment
# spans run ~700ms
curl -s 'localhost:16686/api/traces?service=order-service&limit=5' | python3 -m json.tool
```

This step is worth doing at least once — it's the proof that the "evidence" the agents
gather later is real operational data, not something invented for the demo.

## 5. Trigger the investigation

```bash
INCIDENT=$(curl -s -X POST localhost:8090/api/incidents \
  -H 'Content-Type: application/json' \
  -d '{"service":"order-service","environment":"local","severity":"HIGH","description":"Order creation is failing. Customers report checkout errors."}')
echo "$INCIDENT" | python3 -m json.tool
ID=$(echo "$INCIDENT" | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

curl -s -X POST "localhost:8090/api/incidents/$ID/investigate" | python3 -m json.tool
# -> {"incidentId": "...", "status": "INVESTIGATING"}
```

## 6. Watch it work

```bash
curl -s "localhost:8090/api/agents/status" | python3 -m json.tool
```

Once the investigation completes, this shows `METRICS_AGENT`, `LOG_AGENT`,
`TRACE_AGENT` each `COMPLETED`, with real durations and the tool(s) each one called.

## 7. Poll for completion

```bash
watch -n 3 "curl -s localhost:8090/api/incidents/$ID | python3 -m json.tool"
```

The local model's reasoning step can take a few minutes (it's a genuine multi-minute
local LLM generation over a few thousand tokens of evidence + knowledge — see
`plan.md` Phase 7 for the timeout/context-window tuning this needed). Status moves
`PENDING` → `INVESTIGATING` → `COMPLETED` (or `FAILED`, with `failureReason` explaining
why, per spec section 27/29 — it should never silently stay `INVESTIGATING` forever nor
fabricate a result).

## 8. Read the RCA

```bash
curl -s "localhost:8090/api/incidents/$ID" | python3 -m json.tool
curl -s "localhost:8090/api/incidents/$ID/evidence" | python3 -m json.tool
curl -s "localhost:8090/api/incidents/$ID/hypotheses" | python3 -m json.tool
curl -s "localhost:8090/api/incidents/$ID/timeline" | python3 -m json.tool
curl -s "localhost:8090/api/incidents/$ID/recommendations" | python3 -m json.tool
```

What to look for in `probableRootCause`: it should name the timeout misconfiguration
specifically (not just "Payment Service is slow") and reference that Payment's own
latency was within its documented SLA — that's the RAG-grounded part, drawn from
`knowledge/runbooks/payment-service-runbook.md` and
`knowledge/architecture/order-payment-architecture.md`, not something the model would
know without retrieval. `confidence` should be meaningfully high (the evidence is
unambiguous in this scenario). `requiresHumanApproval` should be `true` if a
rollback/config-change recommendation is present (remediation execution itself is
backlog — this platform only ever recommends, per spec section 20).

## 9. Reset

```bash
./scripts/incident-simulator/simulate.sh reset
```

## Other failure modes

```bash
./scripts/incident-simulator/simulate.sh payment-high-latency  # 1500ms, still succeeds - different signature
./scripts/incident-simulator/simulate.sh payment-http-500      # genuine Payment failure, not a timeout
```

Running an investigation against these should produce a *different* probable root
cause each time — that's the point of hypothesis-driven, evidence-based reasoning
rather than a lookup table.
