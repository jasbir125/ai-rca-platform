package com.airca.orderservice.payment;

import java.math.BigDecimal;

public record PaymentClientResponse(String paymentId, String orderId, BigDecimal amount, String status) {
}
