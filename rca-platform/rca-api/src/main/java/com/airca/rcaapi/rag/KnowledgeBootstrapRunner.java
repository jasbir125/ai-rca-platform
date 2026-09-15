package com.airca.rcaapi.rag;

import com.airca.rca.framework.rag.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Reconciles pgvector with rca-platform/knowledge/ on every startup, so a fresh
 * environment is investigation-ready and an existing one always reflects the current
 * knowledge files. Previously this skipped entirely if the vector store already had
 * any data ("already ingested"), which meant a knowledge doc added or edited after the
 * first-ever startup was silently never ingested short of manually truncating the
 * table — a real staleness bug, not just a missed optimization. Now safe to run
 * unconditionally every time because {@link DocumentIngestionService#ingestDirectory}
 * is itself idempotent: unchanged files are skipped (no embedding calls), changed
 * files are replaced, and removed files have their vectors cleaned up.
 */
@Component
public class KnowledgeBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrapRunner.class);

    private final DocumentIngestionService ingestionService;
    private final Path knowledgeBasePath;

    public KnowledgeBootstrapRunner(
            DocumentIngestionService ingestionService,
            @Value("${rag.knowledge-base-path}") String knowledgeBasePath) {
        this.ingestionService = ingestionService;
        this.knowledgeBasePath = Path.of(knowledgeBasePath);
    }

    @Override
    public void run(String... args) {
        if (!knowledgeBasePath.toFile().isDirectory()) {
            log.warn("rag_bootstrap_skipped reason=knowledge_base_path_not_found path={}", knowledgeBasePath);
            return;
        }

        int chunksWritten = ingestionService.ingestDirectory(knowledgeBasePath);
        log.info("rag_bootstrap_complete chunks_written={} path={}", chunksWritten, knowledgeBasePath);
    }
}
