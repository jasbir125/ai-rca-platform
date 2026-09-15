package com.airca.orderservice.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String itemDescription,
        BigDecimal amount,
        OrderStatus status,
        String paymentId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getItemDescription(),
                order.getAmount(),
                order.getStatus(),
                order.getPaymentId(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
