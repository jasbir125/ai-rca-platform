package com.airca.rcaapi.rag;

import com.airca.rca.framework.rag.DocumentIngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * Operator-triggered knowledge refresh (spec section 42 territory) — without this,
 * picking up a new or edited runbook/architecture doc required a full app restart
 * (the only place {@link DocumentIngestionService#ingestDirectory} was ever called was
 * {@link KnowledgeBootstrapRunner} on startup). Safe to call anytime: ingestion is
 * idempotent, so calling this with nothing changed is a fast no-op.
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    public record ReingestResponse(int chunksWritten) {
    }

    private final DocumentIngestionService ingestionService;
    private final Path knowledgeBasePath;

    public KnowledgeController(
            DocumentIngestionService ingestionService,
            @Value("${rag.knowledge-base-path}") String knowledgeBasePath) {
        this.ingestionService = ingestionService;
        this.knowledgeBasePath = Path.of(knowledgeBasePath);
    }

    @PostMapping("/reingest")
    public ReingestResponse reingest() {
        int chunksWritten = ingestionService.ingestDirectory(knowledgeBasePath);
        return new ReingestResponse(chunksWritten);
    }
}
