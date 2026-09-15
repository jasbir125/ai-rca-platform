package com.airca.rca.framework.rag;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the simple {@code ---\nkey: value\n---} metadata header each knowledge
 * document starts with (service, environment, documentType, domain, tags — spec
 * section 9's required metadata fields) from its body text. Kept dependency-free and
 * separate from ingestion so the parsing rules are unit-testable without a database.
 */
@Component
public class FrontmatterParser {

    private static final String DELIMITER = "---";

    public ParsedDocument parse(String rawDocument) {
        String trimmed = rawDocument.stripLeading();
        if (!trimmed.startsWith(DELIMITER)) {
            return new ParsedDocument(Map.of(), rawDocument.trim());
        }

        int headerEnd = trimmed.indexOf("\n" + DELIMITER, DELIMITER.length());
        if (headerEnd == -1) {
            return new ParsedDocument(Map.of(), rawDocument.trim());
        }

        String header = trimmed.substring(DELIMITER.length(), headerEnd).trim();
        String body = trimmed.substring(headerEnd + ("\n" + DELIMITER).length()).trim();

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String line : header.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon == -1) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            metadata.put(key, value);
        }

        return new ParsedDocument(metadata, body);
    }
}
