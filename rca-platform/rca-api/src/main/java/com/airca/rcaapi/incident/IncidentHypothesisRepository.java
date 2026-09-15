package com.airca.rcaapi.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentHypothesisRepository extends JpaRepository<IncidentHypothesisEntity, UUID> {
    List<IncidentHypothesisEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
