package com.airca.rcaapi.incident;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        String source,
        String service,
        String type,
        String description,
        String value,
        String confidence,
        String correlationId,
        String reference,
        Instant capturedAt) {

    public static EvidenceResponse from(IncidentEvidenceEntity entity) {
        return new EvidenceResponse(
                entity.getId(), entity.getSource(), entity.getService(), entity.getType(), entity.getDescription(),
                entity.getValue(), entity.getConfidence(), entity.getCorrelationId(), entity.getReference(),
                entity.getCapturedAt());
    }
}
