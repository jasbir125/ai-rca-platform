---
service: order-service
environment: production
documentType: PREVIOUS_RCA
domain: order-management
tags: order-service, payment-service, timeout, deployment, configuration
---

# Incident RCA: INC-2026-0614 — Order creation failures after order-service deploy

**Date**: 2026-06-14
**Service**: order-service
**Severity**: HIGH
**Duration**: 22 minutes

## Summary

Order creation began failing at a high rate immediately following an order-service
deployment. All failures were `PAYMENT_TIMEOUT`. Payment Service's own metrics (latency,
error rate) were unaffected throughout the incident window.

## Root cause

The order-service deployment changed `PAYMENT_CALL_TIMEOUT_MS` from its previous value
of 3000ms down to 500ms as an unintended side effect of a configuration template
change. Payment Service's real processing latency at the time was approximately 700ms,
which is within its documented SLA (p99 under 1000ms). Because the new timeout (500ms)
was below Payment Service's actual latency (~700ms), essentially every payment call
timed out on the Order Service side even though Payment Service was healthy and
successfully processing the requests it received.

## Evidence

- Order Service logs: `errorType=PAYMENT_TIMEOUT` on every failed order, with
  "Payment Service call timed out after 500ms" in the message.
- Payment Service metrics: `payment_processing_duration_seconds` p95 ~700ms throughout
  the incident window, `payment_processed_total{status="failed"}` flat (no increase).
- Git history: the deploy's diff showed `PAYMENT_CALL_TIMEOUT_MS` changed from `3000`
  to `500`.

## Resolution

Reverted the configuration change, restoring `PAYMENT_CALL_TIMEOUT_MS` to 3000ms. Order
creation success rate returned to baseline within one minute of the rollback.

## Lesson

This class of incident — a sudden spike in `PAYMENT_TIMEOUT` failures with Payment
Service's own latency/error metrics unchanged — points to an Order Service-side timeout
misconfiguration, not a Payment Service problem. Always check the timeout configuration
and recent deployment history on Order Service before escalating to the Payments team.
