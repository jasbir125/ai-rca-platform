package com.airca.rcaapi.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_hypotheses")
public class IncidentHypothesisEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String probability;

    @Column(name = "evidence_summary", columnDefinition = "TEXT")
    private String evidenceSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentHypothesisEntity() {
        // JPA
    }

    public IncidentHypothesisEntity(UUID id, UUID incidentId, String description, String probability, String evidenceSummary) {
        this.id = id;
        this.incidentId = incidentId;
        this.description = description;
        this.probability = probability;
        this.evidenceSummary = evidenceSummary;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getDescription() {
        return description;
    }

    public String getProbability() {
        return probability;
    }

    public String getEvidenceSummary() {
        return evidenceSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
