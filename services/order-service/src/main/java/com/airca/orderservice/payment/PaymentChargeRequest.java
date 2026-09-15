package com.airca.orderservice.payment;

import java.math.BigDecimal;

record PaymentChargeRequest(String orderId, BigDecimal amount) {
}
