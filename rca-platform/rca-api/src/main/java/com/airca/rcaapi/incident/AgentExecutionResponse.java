package com.airca.rcaapi.incident;

import java.time.Instant;
import java.util.UUID;

public record AgentExecutionResponse(
        UUID id,
        UUID incidentId,
        String agentName,
        String status,
        long durationMs,
        String toolsUsed,
        String summary,
        String errorMessage,
        Instant createdAt) {

    public static AgentExecutionResponse from(AgentExecutionEntity entity) {
        return new AgentExecutionResponse(
                entity.getId(), entity.getIncidentId(), entity.getAgentName(), entity.getStatus(),
                entity.getDurationMs(), entity.getToolsUsed(), entity.getSummary(), entity.getErrorMessage(),
                entity.getCreatedAt());
    }
}
