package com.airca.rcaapi.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_timeline")
public class IncidentTimelineEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "event_timestamp", nullable = false)
    private String eventTimestamp;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String event;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentTimelineEntity() {
        // JPA
    }

    public IncidentTimelineEntity(UUID id, UUID incidentId, String eventTimestamp, String event) {
        this.id = id;
        this.incidentId = incidentId;
        this.eventTimestamp = eventTimestamp;
        this.event = event;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getEventTimestamp() {
        return eventTimestamp;
    }

    public String getEvent() {
        return event;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
