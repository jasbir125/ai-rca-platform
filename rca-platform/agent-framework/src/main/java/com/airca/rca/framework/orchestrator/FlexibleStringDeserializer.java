package com.airca.rca.framework.orchestrator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

/**
 * Local models don't always honor a {@code List<String>} JSON schema faithfully —
 * observed live: qwen3.5 returned a list of objects (e.g.
 * {@code {"incidentId": "...", "description": "..."}}) for
 * {@link RcaReport#similarIncidents()} instead of plain strings, which crashed
 * structured-output parsing (Jackson's {@code MismatchedInputException}) after the
 * full multi-minute generation had already completed — the worst possible place for a
 * strict-parsing failure, since the entire investigation's cost is wasted at the very
 * last step. Same defensive philosophy as {@link RcaReport.Hypothesis#probability()}
 * (kept as a lenient String rather than an enum for the same reason): tolerate the
 * shape variance instead of crashing over it. A JSON string passes through unchanged; a
 * JSON object is flattened to one readable line using whichever common key it has,
 * falling back to the object's compact form rather than failing outright.
 */
public class FlexibleStringDeserializer extends JsonDeserializer<String> {

    private static final List<String> PREFERRED_KEYS = List.of(
            "description", "summary", "text", "incidentId", "id", "title", "name", "action");

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            for (String key : PREFERRED_KEYS) {
                if (node.hasNonNull(key)) {
                    return node.get(key).asText();
                }
            }
            return node.toString();
        }
        return node.asText();
    }
}
