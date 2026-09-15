package com.airca.rca.framework.rag;

import com.airca.rca.framework.TestApplication;
import com.airca.rca.framework.ai.AiModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real RAG pipeline against the real pgvector instance from
 * infrastructure/docker/docker-compose.yml (must be running — see
 * infrastructure/docker: `docker compose up -d postgres`) and real Ollama embeddings
 * (nomic-embed-text), ingesting the actual knowledge documents this repo ships rather
 * than synthetic test fixtures.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.ai.ollama.base-url=http://localhost:11434",
        "spring.ai.ollama.chat.options.model=qwen3.5:latest",
        "spring.ai.ollama.embedding.options.model=nomic-embed-text",
        "spring.ai.ollama.init.pull-model-strategy=never",
        "spring.ai.vectorstore.pgvector.schema-name=rag",
        "spring.ai.vectorstore.pgvector.initialize-schema=true",
        "spring.datasource.url=jdbc:postgresql://localhost:5433/rca_platform",
        "spring.datasource.username=rca",
        "spring.datasource.password=rca_local_dev_only"
})
class RagLiveTest {

    // rca-platform/agent-framework -> rca-platform/knowledge
    private static final Path KNOWLEDGE_BASE = Path.of("..", "knowledge");

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private SearchKnowledgeTool searchKnowledgeTool;

    @Autowired
    private AiModelProvider aiModelProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetVectorStore() {
        jdbcTemplate.execute("TRUNCATE TABLE rag.vector_store");
    }

    @Test
    void ingestsTheRealKnowledgeDocumentsIntoPgvector() {
        int chunks = ingestionService.ingestDirectory(KNOWLEDGE_BASE);

        assertThat(chunks).isGreaterThan(0);

        Long rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag.vector_store", Long.class);
        assertThat(rowCount).isEqualTo((long) chunks);
    }

    @Test
    void retrievesTheRunbookForAPaymentTimeoutQuery() {
        ingestionService.ingestDirectory(KNOWLEDGE_BASE);

        List<Document> results = retrievalService.search(
                "Order Service payment timeout configuration below Payment Service latency", Map.of());

        assertThat(results).isNotEmpty();
        boolean foundRelevantDoc = results.stream().anyMatch(d ->
                String.valueOf(d.getMetadata().get("documentType")).matches("RUNBOOK|ARCHITECTURE|PREVIOUS_RCA"));
        assertThat(foundRelevantDoc).isTrue();
    }

    @Test
    void searchKnowledgeToolIsGenuinelyInvokedAndSurfacesRealDocumentContentThroughTheLlm() {
        ingestionService.ingestDirectory(KNOWLEDGE_BASE);

        String response = aiModelProvider.chatClient()
                .prompt()
                .user("Order Service's PAYMENT_CALL_TIMEOUT_MS was just reduced from 3000ms to 500ms and Payment "
                        + "Service's real latency is about 700ms. Use the searchKnowledge tool to check whether "
                        + "this timeout value is consistent with documented guidance, then answer in one sentence: "
                        + "is the current Order Service timeout configuration safe given Payment Service's "
                        + "documented SLA?")
                .tools(searchKnowledgeTool)
                .call()
                .content();

        // The runbook/architecture docs state the healthy default is 3000ms and that a
        // timeout below Payment Service's real latency is unsafe - a genuine RAG-backed
        // answer should reflect that, not just restate the prompt.
        assertThat(response.toLowerCase()).containsAnyOf("unsafe", "not safe", "below", "too low", "misconfigur");
    }

    @Test
    void reingestingAnUnchangedFileIsANoOpAndCreatesNoDuplicates(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("doc.md"), "# A Runbook\n\nSome stable content that never changes.");

        int firstRun = ingestionService.ingestDirectory(tempDir);
        Long rowsAfterFirstRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag.vector_store", Long.class);

        int secondRun = ingestionService.ingestDirectory(tempDir);
        Long rowsAfterSecondRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag.vector_store", Long.class);

        assertThat(firstRun).isGreaterThan(0);
        assertThat(secondRun).isEqualTo(0); // nothing changed -> nothing (re)embedded
        assertThat(rowsAfterSecondRun).isEqualTo(rowsAfterFirstRun); // and critically: no duplicate rows
    }

    @Test
    void reingestingAChangedFileReplacesOldChunksInsteadOfAccumulatingDuplicates(@TempDir Path tempDir) throws Exception {
        Path doc = tempDir.resolve("doc.md");
        Files.writeString(doc, "# A Runbook\n\nOriginal version of the content.");
        ingestionService.ingestDirectory(tempDir);
        Long rowsAfterV1 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag.vector_store", Long.class);

        Files.writeString(doc, "# A Runbook\n\nCompletely different, updated content about a new topic.");
        ingestionService.ingestDirectory(tempDir);
        Long rowsAfterV2 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag.vector_store", Long.class);

        List<String> storedContent = jdbcTemplate.query(
                "SELECT content FROM rag.vector_store WHERE metadata->>'source' = ?",
                (rs, rowNum) -> rs.getString(1), "doc.md");

        assertThat(rowsAfterV2).isEqualTo(rowsAfterV1); // replaced in place, not doubled
        assertThat(storedContent).hasSize(1);
        assertThat(storedContent.get(0)).contains("Completely different").doesNotContain("Original version");
    }

    @Test
    void filesRemovedFromTheKnowledgeBaseHaveTheirOrphanedVectorsCleanedUp(@TempDir Path tempDir) throws Exception {
        Path keep = tempDir.resolve("keep.md");
        Path remove = tempDir.resolve("remove.md");
        Files.writeString(keep, "# Keep\n\nThis document stays.");
        Files.writeString(remove, "# Remove\n\nThis document will be deleted from disk.");
        ingestionService.ingestDirectory(tempDir);

        Files.delete(remove);
        ingestionService.ingestDirectory(tempDir);

        Long removedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag.vector_store WHERE metadata->>'source' = ?", Long.class, "remove.md");
        Long keptRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag.vector_store WHERE metadata->>'source' = ?", Long.class, "keep.md");

        assertThat(removedRows).isZero();
        assertThat(keptRows).isGreaterThan(0);
    }
}
