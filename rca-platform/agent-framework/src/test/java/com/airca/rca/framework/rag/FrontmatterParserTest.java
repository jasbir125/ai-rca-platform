package com.airca.rca.framework.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontmatterParserTest {

    private final FrontmatterParser parser = new FrontmatterParser();

    @Test
    void parsesMetadataHeaderAndBody() {
        String raw = """
                ---
                service: payment-service
                environment: production
                documentType: RUNBOOK
                domain: payments
                tags: payment-service, latency
                ---

                # Payment Service Runbook

                Some content here.
                """;

        ParsedDocument parsed = parser.parse(raw);

        assertThat(parsed.metadata())
                .containsEntry("service", "payment-service")
                .containsEntry("environment", "production")
                .containsEntry("documentType", "RUNBOOK")
                .containsEntry("domain", "payments");
        assertThat(parsed.content()).contains("# Payment Service Runbook").contains("Some content here.");
        assertThat(parsed.content()).doesNotContain("---");
    }

    @Test
    void treatsDocumentWithoutFrontmatterAsPlainContent() {
        ParsedDocument parsed = parser.parse("Just plain text, no header.");

        assertThat(parsed.metadata()).isEmpty();
        assertThat(parsed.content()).isEqualTo("Just plain text, no header.");
    }
}
