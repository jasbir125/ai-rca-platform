package com.airca.rca.framework.orchestrator;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * The structured RCA output shape from spec section 15, extracted from the LLM via
 * Spring AI's structured-output converter (a JSON schema derived from this record is
 * appended to the prompt, and the model's response is parsed back into it) rather than
 * hand-parsed JSON — this is what "Validate all LLM outputs" (spec section 61) means in
 * practice: a malformed response fails deserialization loudly instead of being
 * silently misinterpreted.
 *
 * <p>The three free-text list fields use {@link FlexibleStringDeserializer} rather than
 * plain {@code List<String>} parsing: observed live, qwen3.5 sometimes returns a list
 * of objects for one of these instead of strings, and failing the whole parse over that
 * — after the multi-minute generation has already completed — throws away a
 * genuinely-reasoned response over a formatting slip. Same tolerant-of-shape-variance
 * philosophy as {@link Hypothesis#probability()} below.
 */
public record RcaReport(
        String summary,
        String probableRootCause,
        double confidence,
        Impact impact,
        List<TimelineEntry> timeline,
        List<Hypothesis> hypotheses,
        @JsonDeserialize(contentUsing = FlexibleStringDeserializer.class) List<String> recommendations,
        @JsonDeserialize(contentUsing = FlexibleStringDeserializer.class) List<String> similarIncidents,
        @JsonDeserialize(contentUsing = FlexibleStringDeserializer.class) List<String> nextActions,
        boolean requiresHumanApproval) {

    public record Impact(List<String> services, List<String> businessFlows, String estimatedImpact) {
    }

    public record TimelineEntry(String timestamp, String event) {
    }

    /**
     * {@code probability} uses the same Confirmed/Highly probable/Probable/Possible/
     * Unknown vocabulary as {@link com.airca.rca.framework.evidence.ConfidenceLevel}
     * (spec section 14) — kept as a String here rather than the enum so a slightly
     * off-format model response doesn't fail the whole structured-output parse; the
     * orchestrator normalizes it when persisting.
     */
    public record Hypothesis(String description, String probability, String evidenceSummary) {
    }
}
