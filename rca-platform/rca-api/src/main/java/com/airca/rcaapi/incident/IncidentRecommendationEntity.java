package com.airca.rcaapi.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_recommendations")
public class IncidentRecommendationEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentRecommendationEntity() {
        // JPA
    }

    public IncidentRecommendationEntity(UUID id, UUID incidentId, String recommendation) {
        this.id = id;
        this.incidentId = incidentId;
        this.recommendation = recommendation;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
