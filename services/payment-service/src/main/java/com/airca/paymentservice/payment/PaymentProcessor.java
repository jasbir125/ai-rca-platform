package com.airca.paymentservice.payment;

import com.airca.paymentservice.chaos.ChaosState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates a downstream payment processor. Latency and failure rate are live-tunable
 * via {@link ChaosState} so the incident simulator can inject faults without a redeploy.
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final ChaosState chaosState;
    private final Timer processingTimer;
    private final Counter successCounter;
    private final Counter failureCounter;

    public PaymentProcessor(ChaosState chaosState, MeterRegistry meterRegistry) {
        this.chaosState = chaosState;
        this.processingTimer = Timer.builder("payment_processing_duration_seconds")
                .description("Time spent simulating payment processing")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.successCounter = Counter.builder("payment_processed_total")
                .tag("status", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("payment_processed_total")
                .tag("status", "failed")
                .register(meterRegistry);
    }

    public PaymentResponse process(PaymentRequest request) {
        long latencyMs = jitter(chaosState.latencyMs());
        double failureRate = chaosState.failureRate();

        return processingTimer.record(() -> {
            simulateLatency(latencyMs);

            if (ThreadLocalRandom.current().nextDouble() < failureRate) {
                failureCounter.increment();
                log.error(
                        "payment_failed orderId={} amount={} simulatedLatencyMs={} errorType={}",
                        request.orderId(), request.amount(), latencyMs, "PAYMENT_DECLINED");
                throw new PaymentProcessingException(
                        "Payment declined for order " + request.orderId());
            }

            successCounter.increment();
            PaymentResponse response = new PaymentResponse(
                    UUID.randomUUID().toString(),
                    request.orderId(),
                    request.amount(),
                    PaymentStatus.SUCCESS,
                    Instant.now());
            log.info(
                    "payment_processed orderId={} paymentId={} amount={} simulatedLatencyMs={}",
                    request.orderId(), response.paymentId(), request.amount(), latencyMs);
            return response;
        });
    }

    private void simulateLatency(long latencyMs) {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProcessingException("Payment processing interrupted");
        }
    }

    /** +/-10% jitter so latency looks like a real service rather than a fixed constant. */
    private long jitter(long baseMs) {
        double factor = 0.9 + ThreadLocalRandom.current().nextDouble() * 0.2;
        return Math.round(baseMs * factor);
    }
}
