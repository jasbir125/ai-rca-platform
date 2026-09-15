package com.airca.paymentservice.payment;

import com.airca.paymentservice.chaos.ChaosState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProcessorTest {

    @Test
    void processesSuccessfullyWhenFailureRateIsZero() {
        ChaosState chaosState = new ChaosState(0, 0.0);
        PaymentProcessor processor = new PaymentProcessor(chaosState, new SimpleMeterRegistry());

        PaymentResponse response = processor.process(new PaymentRequest("order-1", new BigDecimal("49.99")));

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.paymentId()).isNotBlank();
    }

    @Test
    void throwsWhenFailureRateIsOne() {
        ChaosState chaosState = new ChaosState(0, 1.0);
        PaymentProcessor processor = new PaymentProcessor(chaosState, new SimpleMeterRegistry());

        assertThatThrownBy(() -> processor.process(new PaymentRequest("order-2", BigDecimal.TEN)))
                .isInstanceOf(PaymentProcessingException.class);
    }

    @Test
    void honorsLiveChaosLatencyUpdate() {
        ChaosState chaosState = new ChaosState(0, 0.0);
        PaymentProcessor processor = new PaymentProcessor(chaosState, new SimpleMeterRegistry());
        chaosState.setLatencyMs(150);

        long start = System.currentTimeMillis();
        processor.process(new PaymentRequest("order-3", BigDecimal.ONE));
        long elapsed = System.currentTimeMillis() - start;

        // jitter is +/-10%, so at minimum ~135ms should have elapsed.
        assertThat(elapsed).isGreaterThanOrEqualTo(130);
    }
}
