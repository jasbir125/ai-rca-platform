package com.airca.rcaapi.incident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String service,
        String environment,
        String severity,
        String description,
        IncidentStatus status,
        Instant windowStart,
        Instant windowEnd,
        String summary,
        String probableRootCause,
        Double confidence,
        Boolean requiresHumanApproval,
        String failureReason,
        List<String> impactServices,
        List<String> impactBusinessFlows,
        String estimatedImpact,
        List<String> similarIncidents,
        List<String> nextActions,
        Instant createdAt,
        Instant updatedAt) {

    public static IncidentResponse from(IncidentEntity entity) {
        return new IncidentResponse(
                entity.getId(), entity.getService(), entity.getEnvironment(), entity.getSeverity(),
                entity.getDescription(), entity.getStatus(), entity.getWindowStart(), entity.getWindowEnd(),
                entity.getSummary(), entity.getProbableRootCause(), entity.getConfidence(),
                entity.getRequiresHumanApproval(), entity.getFailureReason(),
                IncidentEntity.split(entity.getImpactServices()), IncidentEntity.split(entity.getImpactBusinessFlows()),
                entity.getEstimatedImpact(), IncidentEntity.split(entity.getSimilarIncidents()),
                IncidentEntity.split(entity.getNextActions()), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
