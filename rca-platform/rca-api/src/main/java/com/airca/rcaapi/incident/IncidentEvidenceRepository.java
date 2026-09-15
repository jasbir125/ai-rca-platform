package com.airca.rcaapi.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentEvidenceRepository extends JpaRepository<IncidentEvidenceEntity, UUID> {
    List<IncidentEvidenceEntity> findByIncidentIdOrderByCapturedAtAsc(UUID incidentId);
}
