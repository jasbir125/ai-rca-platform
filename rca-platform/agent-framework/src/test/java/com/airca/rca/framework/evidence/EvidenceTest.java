package com.airca.rca.framework.evidence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceTest {

    @Test
    void buildsWithAllFields() {
        Evidence evidence = Evidence.builder()
                .source("GitLab")
                .service("order-service")
                .type(EvidenceType.CODE_CHANGE)
                .description("Payment timeout changed")
                .value("3000ms -> 500ms")
                .confidence(ConfidenceLevel.CONFIRMED)
                .correlationId("corr-1")
                .reference("commit abc123")
                .build();

        assertThat(evidence.source()).isEqualTo("GitLab");
        assertThat(evidence.type()).isEqualTo(EvidenceType.CODE_CHANGE);
        assertThat(evidence.confidence()).isEqualTo(ConfidenceLevel.CONFIRMED);
        assertThat(evidence.timestamp()).isNotNull();
    }

    @Test
    void defaultsConfidenceToUnknownWhenNotSet() {
        Evidence evidence = Evidence.builder()
                .source("Prometheus")
                .type(EvidenceType.METRIC)
                .description("error rate spiked")
                .build();

        assertThat(evidence.confidence()).isEqualTo(ConfidenceLevel.UNKNOWN);
    }

    @Test
    void rejectsMissingSource() {
        assertThatThrownBy(() -> new Evidence(null, Instant.now(), "svc", EvidenceType.LOG, "desc", null,
                ConfidenceLevel.PROBABLE, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void rejectsMissingDescription() {
        assertThatThrownBy(() -> Evidence.builder().source("Loki").type(EvidenceType.LOG).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }
}
