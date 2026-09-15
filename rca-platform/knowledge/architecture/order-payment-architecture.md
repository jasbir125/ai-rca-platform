---
service: order-service
environment: production
documentType: ARCHITECTURE
domain: order-management
tags: order-service, payment-service, dependencies, timeout
---

# Order Service / Payment Service Architecture

## Overview

Order Service is the entry point for placing an order. On `POST /api/orders` it:

1. Persists a new order in `PENDING` status.
2. Calls Payment Service synchronously over HTTP (`POST /api/payments`) to charge the
   customer.
3. On a successful payment, marks the order `CONFIRMED` and records the returned
   `paymentId`.
4. On a failed or timed-out payment call, marks the order `FAILED` and records a
   `failureReason` (`PAYMENT_TIMEOUT` or `PAYMENT_CALL_FAILED`).

## Dependency

Order Service's only synchronous downstream dependency is Payment Service. There is no
retry on the payment call: a single timeout or failure immediately fails the order.
Order Service does not call any database other than its own.

## Timeout configuration

Order Service calls Payment Service with a configurable timeout,
`PAYMENT_CALL_TIMEOUT_MS`, applied to both the connection and the read phase of the
call. This value must always be set comfortably above Payment Service's expected
processing latency (see the Payment Service Runbook and SLA documents) — if the timeout
is at or below Payment Service's real latency, every order will fail even though
Payment Service itself is healthy. The healthy default is 3000ms.

## Failure classification

- `PAYMENT_TIMEOUT`: the call to Payment Service did not receive a response within
  `PAYMENT_CALL_TIMEOUT_MS`. This is a client-side timeout, not necessarily a Payment
  Service failure — Payment Service may still be processing the request successfully
  when Order Service gives up waiting.
- `PAYMENT_CALL_FAILED`: Payment Service returned an error response (e.g. HTTP 500) or
  was unreachable.

These are deliberately distinct because they point to different root causes: a
`PAYMENT_TIMEOUT` spike with Payment Service's own latency and error-rate metrics
unchanged usually indicates an Order Service-side configuration problem (timeout set
too low), not a Payment Service incident.
