package com.airca.rca.framework.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Retrieves enterprise/application knowledge (architecture, runbooks, SLAs, previous
 * RCAs) with metadata filtering (spec section 9: "service = order-service,
 * environment = production" style queries) — deliberately separate from live
 * operational data, which agents fetch via tools, not RAG (spec section 63).
 */
@Service
public class KnowledgeRetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    private final VectorStore vectorStore;

    public KnowledgeRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> search(String query, Map<String, String> metadataFilters) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD);

        String filterExpression = toFilterExpression(metadataFilters);
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(builder.build());
    }

    private String toFilterExpression(Map<String, String> metadataFilters) {
        if (metadataFilters == null || metadataFilters.isEmpty()) {
            return null;
        }
        return metadataFilters.entrySet().stream()
                .map(e -> "%s == '%s'".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(" AND "));
    }
}
