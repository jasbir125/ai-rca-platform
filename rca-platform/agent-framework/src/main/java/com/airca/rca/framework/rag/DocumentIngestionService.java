package com.airca.rca.framework.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Document -> Loader -> Parser -> Chunking -> Metadata extraction -> Embedding ->
 * pgvector (spec section 9's RAG pipeline). Loads knowledge documents from the
 * filesystem rather than the classpath so the same knowledge/ directory can be mounted
 * or updated without rebuilding the jar.
 *
 * <p>Idempotent by design (production requirement, not just a startup convenience):
 * every file's raw content is hashed (SHA-256), and that hash is compared against what
 * is already stored for that {@code source} before doing any embedding work. Unchanged
 * files are skipped entirely (zero embedding calls); changed files have their old
 * chunks deleted and replaced; files removed from the knowledge base have their orphaned
 * vectors cleaned up. This means {@link #ingestDirectory} is safe to call repeatedly —
 * on every app startup, or from an operator-triggered endpoint — without ever
 * accumulating duplicate or stale rows, which a plain "insert on every call" approach
 * (the previous implementation) does not guarantee.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final String CONTENT_HASH_KEY = "contentHash";
    private static final String SOURCE_KEY = "source";

    private final VectorStore vectorStore;
    private final FrontmatterParser frontmatterParser;
    private final TokenTextSplitter splitter;
    private final JdbcTemplate jdbcTemplate;

    public DocumentIngestionService(VectorStore vectorStore, FrontmatterParser frontmatterParser, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.frontmatterParser = frontmatterParser;
        this.jdbcTemplate = jdbcTemplate;
        this.splitter = new TokenTextSplitter();
    }

    /**
     * Reconciles pgvector with every {@code .md}/{@code .txt} file under
     * {@code knowledgeBasePath} (recursively): ingests new/changed files, skips
     * unchanged ones, and removes vectors for files no longer present. Returns the
     * number of chunks actually written (0 for a fully up-to-date knowledge base).
     */
    public int ingestDirectory(Path knowledgeBasePath) {
        if (!Files.isDirectory(knowledgeBasePath)) {
            throw new IllegalArgumentException("Not a directory: " + knowledgeBasePath);
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(knowledgeBasePath)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".txt"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk knowledge base directory: " + knowledgeBasePath, e);
        }

        int totalChunksWritten = 0;
        for (Path file : files) {
            totalChunksWritten += ingestFile(file);
        }

        Set<String> currentSources = files.stream().map(f -> f.getFileName().toString()).collect(Collectors.toSet());
        int orphansRemoved = removeOrphans(currentSources);

        log.info("rag_ingestion_reconciled files={} chunks_written={} orphans_removed={} path={}",
                files.size(), totalChunksWritten, orphansRemoved, knowledgeBasePath);
        return totalChunksWritten;
    }

    /**
     * Ingests one file if it is new or its content changed since the last ingestion;
     * a no-op (0 chunks, no embedding calls) if the stored hash already matches.
     */
    public int ingestFile(Path file) {
        String raw;
        try {
            raw = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read knowledge document: " + file, e);
        }

        String source = file.getFileName().toString();
        String hash = sha256(raw);

        String existingHash = existingHash(source);
        if (hash.equals(existingHash)) {
            log.debug("rag_document_unchanged file={} skipped", source);
            return 0;
        }

        if (existingHash != null) {
            vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_KEY, source).build());
            log.info("rag_document_stale_chunks_removed file={}", source);
        }

        ParsedDocument parsed = frontmatterParser.parse(raw);
        Document sourceDocument = new Document(parsed.content(), parsed.metadata());
        List<Document> chunks = splitter.apply(List.of(sourceDocument));

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put(SOURCE_KEY, source);
            chunk.getMetadata().put("chunkIndex", i);
            chunk.getMetadata().put(CONTENT_HASH_KEY, hash);
            chunk.getMetadata().put("ingestedAt", Instant.now().toString());
        }

        vectorStore.add(chunks);
        log.info("rag_document_ingested file={} chunks={} hash={}", source, chunks.size(), hash.substring(0, 12));
        return chunks.size();
    }

    /** Exact-match existence check via direct SQL rather than similarity search — this
     *  is a bookkeeping lookup, not a semantic query, so it should not depend on
     *  embedding/similarity-threshold behavior (which is approximate by design) and
     *  should cost zero embedding calls. */
    private String existingHash(String source) {
        List<String> hashes = jdbcTemplate.query(
                "SELECT metadata->>'" + CONTENT_HASH_KEY + "' FROM rag.vector_store WHERE metadata->>'" + SOURCE_KEY + "' = ? LIMIT 1",
                (rs, rowNum) -> rs.getString(1), source);
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    private int removeOrphans(Set<String> currentSources) {
        List<String> storedSources = jdbcTemplate.query(
                "SELECT DISTINCT metadata->>'" + SOURCE_KEY + "' FROM rag.vector_store WHERE metadata->>'" + SOURCE_KEY + "' IS NOT NULL",
                (rs, rowNum) -> rs.getString(1));

        int removed = 0;
        for (String stored : storedSources) {
            if (!currentSources.contains(stored)) {
                vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_KEY, stored).build());
                log.info("rag_orphan_removed source={}", stored);
                removed++;
            }
        }
        return removed;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
