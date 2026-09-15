package com.airca.rcaapi.incident;

import java.util.UUID;

public record HypothesisResponse(UUID id, String description, String probability, String evidenceSummary) {

    public static HypothesisResponse from(IncidentHypothesisEntity entity) {
        return new HypothesisResponse(entity.getId(), entity.getDescription(), entity.getProbability(), entity.getEvidenceSummary());
    }
}
