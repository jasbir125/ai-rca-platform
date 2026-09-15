package com.airca.rca.framework.rag;

import com.airca.rca.framework.tool.RcaTool;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The {@code searchKnowledge(query, metadata)} tool from spec section 12, exposed to
 * agents/orchestrator via Spring AI tool calling. Formats results as plain text with
 * their source and metadata so the LLM can cite where a claim came from.
 */
@Component
public class SearchKnowledgeTool implements RcaTool {

    private final KnowledgeRetrievalService retrievalService;

    public SearchKnowledgeTool(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String toolName() {
        return "searchKnowledge";
    }

    @Tool(name = "searchKnowledge",
            description = "Search enterprise knowledge (architecture docs, runbooks, API specs, SLAs, "
                    + "previous RCA reports) for context relevant to an investigation. Optionally scope "
                    + "results to one service.")
    public String searchKnowledge(
            @ToolParam(description = "the search query, e.g. 'payment service timeout SLA'") String query,
            @ToolParam(description = "optional service name to filter results, or empty for no filter",
                    required = false) String service) {

        Map<String, String> filters = (service == null || service.isBlank())
                ? Map.of()
                : Map.of("service", service);

        List<Document> results = retrievalService.search(query, filters);

        if (results.isEmpty()) {
            return "No relevant knowledge found for query: " + query;
        }

        StringBuilder sb = new StringBuilder();
        for (Document doc : results) {
            sb.append("Source: ").append(doc.getMetadata().getOrDefault("source", "unknown"))
                    .append(" | documentType: ").append(doc.getMetadata().getOrDefault("documentType", "unknown"))
                    .append(" | service: ").append(doc.getMetadata().getOrDefault("service", "unknown"))
                    .append('\n')
                    .append(doc.getText())
                    .append("\n---\n");
        }
        return sb.toString();
    }
}
