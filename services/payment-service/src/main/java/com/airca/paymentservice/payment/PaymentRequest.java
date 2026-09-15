package com.airca.paymentservice.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String orderId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
