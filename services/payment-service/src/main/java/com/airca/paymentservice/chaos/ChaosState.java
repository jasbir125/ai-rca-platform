package com.airca.paymentservice.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live-mutable fault-injection knobs for the incident simulator. Backed by env-var
 * defaults (so a fresh process starts healthy) but overridable at runtime via
 * POST /admin/chaos without a redeploy, which is what scripts/incident-simulator drives.
 */
@Component
public class ChaosState {

    private final AtomicLong latencyMs;
    private final AtomicReference<Double> failureRate;

    public ChaosState(
            @Value("${payment.latency-ms:700}") long defaultLatencyMs,
            @Value("${payment.failure-rate:0.0}") double defaultFailureRate) {
        this.latencyMs = new AtomicLong(defaultLatencyMs);
        this.failureRate = new AtomicReference<>(defaultFailureRate);
    }

    public long latencyMs() {
        return latencyMs.get();
    }

    public double failureRate() {
        return failureRate.get();
    }

    public void setLatencyMs(long value) {
        latencyMs.set(value);
    }

    public void setFailureRate(double value) {
        failureRate.set(value);
    }
}
