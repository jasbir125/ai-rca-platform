package com.airca.orderservice.error;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorType,
        String message,
        String correlationId) {
}
