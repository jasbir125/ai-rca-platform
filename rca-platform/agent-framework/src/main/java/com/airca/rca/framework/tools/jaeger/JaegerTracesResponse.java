package com.airca.rca.framework.tools.jaeger;

import java.util.List;

/** Shape of Jaeger's {@code /api/traces} and {@code /api/traces/{traceId}} responses. */
public record JaegerTracesResponse(List<JaegerTrace> data) {

    public record JaegerTrace(String traceID, List<JaegerSpan> spans) {
    }

    public record JaegerSpan(String spanID, String operationName, long duration, List<JaegerTag> tags) {
    }

    public record JaegerTag(String key, Object value) {
    }
}
