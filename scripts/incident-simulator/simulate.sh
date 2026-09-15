#!/usr/bin/env bash
# Incident Simulator (spec section 7). Injects faults into the running
# order-service/payment-service via their /admin/chaos endpoints — no redeploy needed,
# see ChaosState in each service. Not every failure mode from the spec's list of 16 is
# reproducible with just these two services (e.g. Kafka/Kubernetes modes need those
# components, which are backlog per plan.md); this covers what the current MVP stack
# can genuinely trigger.
set -euo pipefail

ORDER_SERVICE_URL="${ORDER_SERVICE_URL:-http://localhost:8081}"
PAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL:-http://localhost:8082}"

usage() {
  cat <<EOF
Usage: $0 <command>

Commands:
  reset                 Restore healthy baseline on both services.
  payment-timeout        Primary demo scenario: order-service's payment call timeout
                          drops from 3000ms to 500ms while Payment still takes ~700ms.
  payment-high-latency    Payment Service latency rises to 1500ms (still within a
                          generous timeout, but breaches its own SLA).
  payment-http-500        Payment Service starts returning HTTP 500 for every request.
  traffic <n>             Create <n> normal orders against the current chaos state.
  status                  Show current chaos state on both services.

Environment:
  ORDER_SERVICE_URL   (default http://localhost:8081)
  PAYMENT_SERVICE_URL (default http://localhost:8082)
EOF
}

chaos_order() {
  curl -s -X POST "$ORDER_SERVICE_URL/admin/chaos" -H 'Content-Type: application/json' -d "$1"
}

chaos_payment() {
  curl -s -X POST "$PAYMENT_SERVICE_URL/admin/chaos" -H 'Content-Type: application/json' -d "$1"
}

cmd_reset() {
  echo "Restoring healthy baseline..."
  chaos_order '{"paymentTimeoutMs":3000}'
  chaos_payment '{"latencyMs":700,"failureRate":0.0}'
  echo
  echo "Done."
}

cmd_payment_timeout() {
  echo "Injecting: order-service v1.1 (PAYMENT_CALL_TIMEOUT_MS 3000ms -> 500ms)."
  echo "Payment Service latency is left at its healthy ~700ms — the point is that the"
  echo "timeout, not Payment, is misconfigured."
  chaos_payment '{"latencyMs":700,"failureRate":0.0}'
  chaos_order '{"paymentTimeoutMs":500}'
  echo
  echo "Injected. Run '$0 traffic 5' to generate failing orders, then trigger an RCA investigation."
}

cmd_payment_high_latency() {
  echo "Injecting: Payment Service latency 1500ms (breaches its documented <1000ms p99 SLA)."
  chaos_order '{"paymentTimeoutMs":3000}'
  chaos_payment '{"latencyMs":1500,"failureRate":0.0}'
  echo
  echo "Injected."
}

cmd_payment_http_500() {
  echo "Injecting: Payment Service returns HTTP 500 for every request."
  chaos_order '{"paymentTimeoutMs":3000}'
  chaos_payment '{"latencyMs":700,"failureRate":1.0}'
  echo
  echo "Injected."
}

cmd_traffic() {
  local count="${1:-5}"
  echo "Generating $count order(s)..."
  for ((i = 1; i <= count; i++)); do
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ORDER_SERVICE_URL/api/orders" \
      -H 'Content-Type: application/json' \
      -d "{\"itemDescription\":\"widget-$i\",\"amount\":9.99}")
    echo "  order $i: HTTP $status"
  done
}

cmd_status() {
  echo "order-service chaos state is not directly queryable (write-only endpoint);"
  echo "check its logs/metrics, or infer from behavior via '$0 traffic 1'."
}

case "${1:-}" in
  reset) cmd_reset ;;
  payment-timeout) cmd_payment_timeout ;;
  payment-high-latency) cmd_payment_high_latency ;;
  payment-http-500) cmd_payment_http_500 ;;
  traffic) cmd_traffic "${2:-5}" ;;
  status) cmd_status ;;
  *) usage; exit 1 ;;
esac
