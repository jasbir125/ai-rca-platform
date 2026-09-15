package com.airca.rca.framework.orchestrator;

import java.time.Instant;

public record InvestigationRequest(
        String incidentId,
        String service,
        String environment,
        String severity,
        String description,
        Instant windowStart,
        Instant windowEnd,
        String correlationId) {
}
