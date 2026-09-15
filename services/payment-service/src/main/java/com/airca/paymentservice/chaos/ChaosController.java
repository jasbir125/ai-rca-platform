package com.airca.paymentservice.chaos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only endpoint used by the incident simulator to inject faults without a
 * redeploy. Not exposed through the API Gateway and not part of the public API surface;
 * in a real deployment this would sit behind a NetworkPolicy / operator-only role.
 */
@RestController
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    private final ChaosState chaosState;

    public ChaosController(ChaosState chaosState) {
        this.chaosState = chaosState;
    }

    public record ChaosRequest(
            @PositiveOrZero Long latencyMs,
            @DecimalMin("0.0") @DecimalMax("1.0") Double failureRate) {
    }

    public record ChaosStatus(long latencyMs, double failureRate) {
    }

    @PostMapping("/admin/chaos")
    public ChaosStatus applyChaos(@RequestBody ChaosRequest request) {
        if (request.latencyMs() != null) {
            chaosState.setLatencyMs(request.latencyMs());
        }
        if (request.failureRate() != null) {
            chaosState.setFailureRate(request.failureRate());
        }
        log.warn("chaos_applied latencyMs={} failureRate={}", chaosState.latencyMs(), chaosState.failureRate());
        return new ChaosStatus(chaosState.latencyMs(), chaosState.failureRate());
    }
}
