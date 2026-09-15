package com.airca.rca.framework.rag;

import java.util.Map;

/** The result of splitting a knowledge document into its metadata header and body text. */
public record ParsedDocument(Map<String, Object> metadata, String content) {
}
