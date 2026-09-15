package com.airca.rcaapi.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, UUID> {
    List<AgentExecutionEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    List<AgentExecutionEntity> findTop50ByOrderByCreatedAtDesc();
}
