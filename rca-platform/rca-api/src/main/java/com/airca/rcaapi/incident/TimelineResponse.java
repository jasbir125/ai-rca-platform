package com.airca.rcaapi.incident;

import java.util.UUID;

public record TimelineResponse(UUID id, String timestamp, String event) {

    public static TimelineResponse from(IncidentTimelineEntity entity) {
        return new TimelineResponse(entity.getId(), entity.getEventTimestamp(), entity.getEvent());
    }
}
