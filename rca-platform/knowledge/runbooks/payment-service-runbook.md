---
service: payment-service
environment: production
documentType: RUNBOOK
domain: payments
tags: payment-service, latency, sla, timeout, troubleshooting
---

# Payment Service Runbook

## Service Level Agreement

Payment Service's documented SLA is: 99th percentile processing latency under 1000ms,
with typical latency around 700ms under normal load. A measured latency within this
range is healthy and within SLA, even if it looks "slow" in isolation.

## Symptom: Order Service reports PAYMENT_TIMEOUT errors

Checklist, in order:

1. Check Payment Service's own error rate and latency metrics
   (`payment_processed_total`, `payment_processing_duration_seconds`) for the affected
   time window. If Payment Service's latency is within its documented SLA (under
   1000ms, typically ~700ms) and its error rate is not elevated, Payment Service itself
   is healthy.
2. If Payment Service is healthy but Order Service is still timing out, check Order
   Service's `PAYMENT_CALL_TIMEOUT_MS` configuration and any recent deployment or
   configuration change to it. A timeout configured below Payment Service's real
   latency will produce exactly this symptom — client-side timeouts with a healthy
   downstream service — and is the most common cause of this alert.
3. Only if Payment Service's own latency/error-rate metrics are also degraded should
   Payment Service infrastructure (CPU, memory, GC pauses, downstream card-network
   dependency) be investigated as the root cause.

## Known issue: timeout misconfiguration on deploy

Payment Service's latency has historically triggered false-positive incidents when a
new Order Service deployment reduces `PAYMENT_CALL_TIMEOUT_MS` without accounting for
Payment Service's real-world latency. The fix is a configuration rollback on Order
Service, not any change to Payment Service. See previous-rcas for a documented
precedent of this exact failure mode.

## Remediation

- If root cause is an Order Service timeout misconfiguration: recommend rolling Order
  Service back to the previous configuration/version, or raising
  `PAYMENT_CALL_TIMEOUT_MS` to a value comfortably above Payment Service's p99 latency
  (at least 3000ms under normal conditions). This is a configuration change and
  requires human approval before being applied.
- If root cause is genuine Payment Service degradation: escalate to the Payments team;
  do not modify Order Service.
