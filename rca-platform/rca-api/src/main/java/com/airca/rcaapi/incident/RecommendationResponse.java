package com.airca.rcaapi.incident;

import java.util.UUID;

public record RecommendationResponse(UUID id, String recommendation) {

    public static RecommendationResponse from(IncidentRecommendationEntity entity) {
        return new RecommendationResponse(entity.getId(), entity.getRecommendation());
    }
}
