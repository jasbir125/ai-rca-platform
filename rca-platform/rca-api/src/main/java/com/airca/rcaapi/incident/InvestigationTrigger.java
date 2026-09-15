package com.airca.rcaapi.incident;

import java.util.UUID;

/** Separated from {@link InvestigationRunner}'s implementation so IncidentService can
 *  be unit-tested against a simple mock instead of needing bytecode instrumentation of
 *  a concrete class. */
public interface InvestigationTrigger {
    void run(UUID incidentId);
}
