package com.airca.orderservice.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live-mutable fault-injection knobs. {@code paymentCallTimeoutMs}: the Order ->
 * Payment call timeout — the primary demo scenario drops this from its healthy default
 * (3000ms) to 500ms, mirroring a bad configuration change. {@code simulateBug}: a
 * genuine unhandled Java exception in {@link com.airca.orderservice.order.OrderService}
 * (an unmapped loyalty-tier lookup causing a real NullPointerException) — a different
 * failure archetype from the config-driven scenarios above: an application code defect,
 * with a real stack trace in the logs, rather than a timeout/latency/error-rate
 * misconfiguration.
 */
@Component
public class ChaosState {

    private final AtomicLong paymentCallTimeoutMs;
    private final AtomicBoolean simulateBug;

    public ChaosState(
            @Value("${payment.call-timeout-ms:3000}") long defaultTimeoutMs,
            @Value("${chaos.simulate-bug:false}") boolean defaultSimulateBug) {
        this.paymentCallTimeoutMs = new AtomicLong(defaultTimeoutMs);
        this.simulateBug = new AtomicBoolean(defaultSimulateBug);
    }

    public long paymentCallTimeoutMs() {
        return paymentCallTimeoutMs.get();
    }

    public void setPaymentCallTimeoutMs(long value) {
        paymentCallTimeoutMs.set(value);
    }

    public boolean simulateBug() {
        return simulateBug.get();
    }

    public void setSimulateBug(boolean value) {
        simulateBug.set(value);
    }
}
