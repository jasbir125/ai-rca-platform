package com.airca.rca.framework.evidence;

import java.time.Instant;

/**
 * A single, standardized fact gathered by an agent or retrieved from RAG. Every claim
 * in an RCA report must trace back to at least one of these — the platform never
 * presents an LLM assertion as fact without an Evidence item backing it.
 */
public record Evidence(
        String source,
        Instant timestamp,
        String service,
        EvidenceType type,
        String description,
        String value,
        ConfidenceLevel confidence,
        String correlationId,
        String reference) {

    public Evidence {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Evidence.source is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Evidence.description is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Evidence.type is required");
        }
        if (confidence == null) {
            confidence = ConfidenceLevel.UNKNOWN;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String source;
        private Instant timestamp = Instant.now();
        private String service;
        private EvidenceType type;
        private String description;
        private String value;
        private ConfidenceLevel confidence = ConfidenceLevel.UNKNOWN;
        private String correlationId;
        private String reference;

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder type(EvidenceType type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder confidence(ConfidenceLevel confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public Evidence build() {
            return new Evidence(source, timestamp, service, type, description, value, confidence, correlationId, reference);
        }
    }
}
