package com.airca.rcaapi.incident;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateIncidentRequest(
        @NotBlank String service,
        @NotBlank String environment,
        @NotBlank String severity,
        @NotBlank String description,
        Instant startTime) {
}
