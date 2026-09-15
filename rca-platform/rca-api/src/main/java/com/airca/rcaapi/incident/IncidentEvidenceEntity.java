package com.airca.rcaapi.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_evidence")
public class IncidentEvidenceEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(nullable = false)
    private String source;

    private String service;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(nullable = false)
    private String confidence;

    @Column(name = "correlation_id")
    private String correlationId;

    private String reference;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentEvidenceEntity() {
        // JPA
    }

    public IncidentEvidenceEntity(UUID id, UUID incidentId, String source, String service, String type,
            String description, String value, String confidence, String correlationId, String reference,
            Instant capturedAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.source = source;
        this.service = service;
        this.type = type;
        this.description = description;
        this.value = value;
        this.confidence = confidence;
        this.correlationId = correlationId;
        this.reference = reference;
        this.capturedAt = capturedAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getSource() {
        return source;
    }

    public String getService() {
        return service;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
