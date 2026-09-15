package com.airca.rcaapi.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentTimelineRepository extends JpaRepository<IncidentTimelineEntity, UUID> {
    List<IncidentTimelineEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
