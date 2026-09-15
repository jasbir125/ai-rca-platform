package com.airca.rca.framework.agent;

import java.time.Instant;

/**
 * What an agent needs to investigate one incident: which service, which environment,
 * the time window to inspect, and identifiers for correlating evidence back to the
 * incident and across services.
 */
public record AgentContext(
        String incidentId,
        String service,
        String environment,
        Instant windowStart,
        Instant windowEnd,
        String correlationId,
        String incidentDescription) {
}
