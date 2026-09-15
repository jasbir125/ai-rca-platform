package com.airca.rcaapi.error;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String errorType, String message) {
}
