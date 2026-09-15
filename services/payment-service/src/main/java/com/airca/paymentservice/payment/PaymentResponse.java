package com.airca.paymentservice.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String paymentId,
        String orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant processedAt) {
}
