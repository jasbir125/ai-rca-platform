package com.airca.rcaapi.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentRecommendationRepository extends JpaRepository<IncidentRecommendationEntity, UUID> {
    List<IncidentRecommendationEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
