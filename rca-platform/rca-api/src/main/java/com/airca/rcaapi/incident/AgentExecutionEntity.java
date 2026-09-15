package com.airca.rcaapi.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Persisted per agent per investigation, for the "Agent Activity" view (spec section 45). */
@Entity
@Table(name = "agent_executions")
public class AgentExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(nullable = false)
    private String status;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "tools_used", columnDefinition = "TEXT")
    private String toolsUsed;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentExecutionEntity() {
        // JPA
    }

    public AgentExecutionEntity(UUID id, UUID incidentId, String agentName, String status, long durationMs,
            String toolsUsed, String summary, String errorMessage) {
        this.id = id;
        this.incidentId = incidentId;
        this.agentName = agentName;
        this.status = status;
        this.durationMs = durationMs;
        this.toolsUsed = toolsUsed;
        this.summary = summary;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getToolsUsed() {
        return toolsUsed;
    }

    public String getSummary() {
        return summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
