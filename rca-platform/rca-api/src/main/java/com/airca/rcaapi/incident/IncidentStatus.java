package com.airca.rcaapi.incident;

/** Spec section 44's incident lifecycle. WAITING_FOR_APPROVAL is reserved for the
 *  remediation/approval workflow (backlog — not reachable in the MVP). */
public enum IncidentStatus {
    PENDING,
    INVESTIGATING,
    COMPLETED,
    FAILED,
    WAITING_FOR_APPROVAL
}
