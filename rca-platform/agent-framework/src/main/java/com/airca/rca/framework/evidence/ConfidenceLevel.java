package com.airca.rca.framework.evidence;

/**
 * How certain a single piece of evidence is. Deliberately separate from the RCA's
 * overall numeric confidence score (section 15/16 of the spec) — this is a per-fact
 * qualifier so the LLM (and the human reading the report) never mistakes a possible
 * explanation for a confirmed one.
 */
public enum ConfidenceLevel {
    CONFIRMED,
    HIGHLY_PROBABLE,
    PROBABLE,
    POSSIBLE,
    UNKNOWN
}
