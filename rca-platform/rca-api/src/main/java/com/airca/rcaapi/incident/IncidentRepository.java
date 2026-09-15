package com.airca.rcaapi.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {
}
