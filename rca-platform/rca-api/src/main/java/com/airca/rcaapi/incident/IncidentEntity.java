package com.airca.rcaapi.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String service;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "probable_root_cause", columnDefinition = "TEXT")
    private String probableRootCause;

    private Double confidence;

    @Column(name = "requires_human_approval")
    private Boolean requiresHumanApproval;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "impact_services", columnDefinition = "TEXT")
    private String impactServices;

    @Column(name = "impact_business_flows", columnDefinition = "TEXT")
    private String impactBusinessFlows;

    @Column(name = "estimated_impact", columnDefinition = "TEXT")
    private String estimatedImpact;

    @Column(name = "similar_incidents", columnDefinition = "TEXT")
    private String similarIncidents;

    @Column(name = "next_actions", columnDefinition = "TEXT")
    private String nextActions;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IncidentEntity() {
        // JPA
    }

    public IncidentEntity(UUID id, String service, String environment, String severity, String description,
            Instant windowStart, Instant windowEnd, String correlationId) {
        this.id = id;
        this.service = service;
        this.environment = environment;
        this.severity = severity;
        this.description = description;
        this.status = IncidentStatus.PENDING;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markInvestigating() {
        this.status = IncidentStatus.INVESTIGATING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(String summary, String probableRootCause, double confidence, boolean requiresHumanApproval,
            List<String> impactServices, List<String> impactBusinessFlows, String estimatedImpact,
            List<String> similarIncidents, List<String> nextActions) {
        this.status = IncidentStatus.COMPLETED;
        this.summary = summary;
        this.probableRootCause = probableRootCause;
        this.confidence = confidence;
        this.requiresHumanApproval = requiresHumanApproval;
        this.impactServices = join(impactServices);
        this.impactBusinessFlows = join(impactBusinessFlows);
        this.estimatedImpact = estimatedImpact;
        this.similarIncidents = join(similarIncidents);
        this.nextActions = join(nextActions);
        this.updatedAt = Instant.now();
    }

    private static String join(List<String> values) {
        return values == null ? null : String.join("\n", values);
    }

    public static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\n"));
    }

    public void markFailed(String failureReason) {
        this.status = IncidentStatus.FAILED;
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getService() {
        return service;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public String getSummary() {
        return summary;
    }

    public String getProbableRootCause() {
        return probableRootCause;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Boolean getRequiresHumanApproval() {
        return requiresHumanApproval;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getImpactServices() {
        return impactServices;
    }

    public String getImpactBusinessFlows() {
        return impactBusinessFlows;
    }

    public String getEstimatedImpact() {
        return estimatedImpact;
    }

    public String getSimilarIncidents() {
        return similarIncidents;
    }

    public String getNextActions() {
        return nextActions;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
