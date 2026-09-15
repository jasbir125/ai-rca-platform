package com.airca.rca.framework.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the exact failure observed live: qwen3.5 returned {@code similarIncidents} as a
 * list of objects instead of strings, and the plain {@code List<String>} parsing that
 * was in place before this test crashed with a Jackson MismatchedInputException after a
 * full ~14-minute generation — wasting the entire investigation. No LLM needed here;
 * this exercises the same Jackson parsing path Spring AI's structured-output converter
 * uses, directly, against a hand-written payload shaped like the real failure.
 */
class RcaReportParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesWhenSimilarIncidentsIsAListOfPlainStrings() throws Exception {
        String json = """
                {
                  "summary": "s", "probableRootCause": "r", "confidence": 0.9,
                  "impact": {"services": [], "businessFlows": [], "estimatedImpact": "e"},
                  "timeline": [], "hypotheses": [],
                  "recommendations": ["do x"],
                  "similarIncidents": ["INC-2026-0614"],
                  "nextActions": ["verify config"],
                  "requiresHumanApproval": true
                }
                """;

        RcaReport report = mapper.readValue(json, RcaReport.class);

        assertThat(report.similarIncidents()).containsExactly("INC-2026-0614");
    }

    @Test
    void toleratesSimilarIncidentsAsAListOfObjectsInsteadOfCrashing() throws Exception {
        // The exact shape observed live from qwen3.5: objects instead of strings.
        String json = """
                {
                  "summary": "s", "probableRootCause": "r", "confidence": 0.9,
                  "impact": {"services": [], "businessFlows": [], "estimatedImpact": "e"},
                  "timeline": [], "hypotheses": [],
                  "recommendations": [{"description": "do x"}],
                  "similarIncidents": [{"incidentId": "INC-2026-0614", "description": "same timeout pattern"}],
                  "nextActions": [{"action": "verify config"}],
                  "requiresHumanApproval": true
                }
                """;

        RcaReport report = mapper.readValue(json, RcaReport.class);

        assertThat(report.recommendations()).containsExactly("do x");
        assertThat(report.similarIncidents()).containsExactly("same timeout pattern"); // "description" preferred over "incidentId"
        assertThat(report.nextActions()).containsExactly("verify config");
    }

    @Test
    void fallsBackToCompactJsonWhenAnObjectHasNoneOfThePreferredKeys() throws Exception {
        String json = """
                {
                  "summary": "s", "probableRootCause": "r", "confidence": 0.9,
                  "impact": {"services": [], "businessFlows": [], "estimatedImpact": "e"},
                  "timeline": [], "hypotheses": [],
                  "recommendations": [], "nextActions": [],
                  "similarIncidents": [{"weirdKey": "value"}],
                  "requiresHumanApproval": false
                }
                """;

        RcaReport report = mapper.readValue(json, RcaReport.class);

        assertThat(report.similarIncidents()).hasSize(1);
        assertThat(report.similarIncidents().get(0)).contains("weirdKey");
    }
}
