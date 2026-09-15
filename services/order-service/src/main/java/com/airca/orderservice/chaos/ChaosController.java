package com.airca.orderservice.chaos;

import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only endpoint used by the incident simulator to inject faults without a
 * redeploy. Not exposed through the API Gateway.
 */
@RestController
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    private final ChaosState chaosState;

    public ChaosController(ChaosState chaosState) {
        this.chaosState = chaosState;
    }

    public record ChaosRequest(@Positive Long paymentTimeoutMs, Boolean simulateBug) {
    }

    public record ChaosStatus(long paymentTimeoutMs, boolean simulateBug) {
    }

    @PostMapping("/admin/chaos")
    public ChaosStatus applyChaos(@RequestBody ChaosRequest request) {
        if (request.paymentTimeoutMs() != null) {
            chaosState.setPaymentCallTimeoutMs(request.paymentTimeoutMs());
        }
        if (request.simulateBug() != null) {
            chaosState.setSimulateBug(request.simulateBug());
        }
        log.warn("chaos_applied paymentCallTimeoutMs={} simulateBug={}",
                chaosState.paymentCallTimeoutMs(), chaosState.simulateBug());
        return new ChaosStatus(chaosState.paymentCallTimeoutMs(), chaosState.simulateBug());
    }
}
