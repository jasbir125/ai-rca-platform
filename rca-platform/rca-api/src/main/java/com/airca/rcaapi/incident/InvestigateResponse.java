package com.airca.rcaapi.incident;

import java.util.UUID;

/** Spec section 44: investigate returns immediately with just id + status. */
public record InvestigateResponse(UUID incidentId, IncidentStatus status) {
}
