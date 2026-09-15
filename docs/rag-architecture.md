# RAG Architecture

## Pipeline

```
knowledge/**/*.md  ->  FrontmatterParser  ->  TokenTextSplitter  ->  metadata attached
    ->  Ollama embeddings (nomic-embed-text, 768d)  ->  pgvector (schema: rag)
```

Implemented in `DocumentIngestionService` (`agent-framework`). Documents live on the
filesystem (`rca-platform/knowledge/`), not the classpath, so they can be edited or
mounted without rebuilding the jar — runbooks and previous-incident reports are
expected to change more often than code (spec section 9).

## Document format

```markdown
---
service: payment-service
environment: production
documentType: RUNBOOK
domain: payments
tags: payment-service, latency, sla, timeout, troubleshooting
---

# Payment Service Runbook
...body...
```

`FrontmatterParser` splits the header into metadata (attached to every chunk of that
document) and hands the body to the chunker. Unit-tested directly
(`FrontmatterParserTest`) without touching a database.

## What's actually in the knowledge base right now

Three real documents (not placeholders — see `plan.md` Phase 4):
`architecture/order-payment-architecture.md` (the Order→Payment dependency and timeout
configuration contract), `runbooks/payment-service-runbook.md` (Payment's SLA and the
specific "timeout misconfiguration on deploy" troubleshooting path),
`previous-rcas/incident-2026-06-payment-timeout.md` (a synthetic but realistic
precedent RCA for exactly the primary demo scenario, so "similar incident found" has
something genuine to retrieve — spec section 19).

## Retrieval

`KnowledgeRetrievalService.search(query, metadataFilters)` wraps Spring AI's
`VectorStore.similaritySearch` (topK=5, similarity threshold 0.5) with an optional
metadata filter expression (`service == 'x' AND environment == 'y'`, spec section 9's
example). Exposed to the LLM as the `searchKnowledge` tool
(`SearchKnowledgeTool`) and used directly (not through tool-calling) by
`InvestigationOrchestrator`'s final reasoning step.

**Deliberately unfiltered by service in the orchestrator's own retrieval call.** An
order-service incident's root cause can live in the Payment Service runbook — filtering
retrieval to `service == 'order-service'` would have hidden exactly the document that
explains the SLA the timeout should have respected. Semantic similarity alone decides
relevance for the final-reasoning retrieval; the `service` filter parameter exists on
`SearchKnowledgeTool` for cases where an agent (or the LLM via tool-calling) genuinely
wants to scope a search, not as a default.

## Live-verified (not just unit-tested)

`RagLiveTest` (`agent-framework`), against the real pgvector container: ingests the
actual 3 knowledge documents, confirms the pgvector row count matches, confirms a real
embedding-similarity search for a payment-timeout query returns one of the real
documents, and confirms a live LLM call with `SearchKnowledgeTool` registered actually
invokes it and produces an answer grounded in the retrieved content (the model
correctly judges a 500ms timeout against Payment's ~700ms latency as unsafe, citing
the retrieved SLA). Independently spot-checked via `docker exec psql`: real 768-dimension
embeddings, correct `documentType` metadata.

## Separation from live operational data

RAG never serves Prometheus/Loki/Jaeger data, and the tools never serve architecture
docs — spec section 63's explicit separation. The knowledge base holds relatively
stable facts (what *should* be true); agents fetch what's *actually* happening right
now. Only the final LLM prompt combines both.

## Idempotent ingestion (production-readiness fix, 2026-09-10)

Ingestion is now safe to run repeatedly — on every app startup, and via an operator
endpoint — without ever accumulating duplicate or stale rows:

- **Content-hash change detection**: each file's raw content is SHA-256 hashed; the
  hash is compared against what's already stored for that `source` via a direct SQL
  lookup (`JdbcTemplate`, not a similarity search — this is an exact bookkeeping check,
  not a semantic one, so it shouldn't depend on embedding/threshold behavior and costs
  zero embedding calls). An unchanged file is skipped entirely.
- **Delete-then-replace on change**: a changed file's old chunks are deleted
  (`VectorStore.delete(Filter.Expression)`, filtered by `source`) before the new ones
  are embedded and inserted — never additive.
- **Orphan cleanup**: after ingesting the current file set, any stored `source` no
  longer present on disk has its vectors removed.
- **`KnowledgeBootstrapRunner`** now reconciles unconditionally on every startup
  instead of skipping if the store merely looked non-empty — the old check meant an
  edited or newly-added knowledge doc was silently never picked up after the very
  first startup, short of manually truncating the table. That was a staleness bug, not
  a missed optimization.
- **`POST /api/knowledge/reingest`** (new): operator-triggered live refresh without an
  app restart. Idempotent, so calling it with nothing changed is a fast no-op.

**Live-verified against the real running container** (not just the test suite):
edited a real knowledge doc, rebuilt/restarted `rca-api`, confirmed the log shows
`rag_document_stale_chunks_removed` followed by a fresh `rag_document_ingested` for
only that file, the row count stayed at 3 (not 4), and the new content was present in
the stored row. Reverted the edit and confirmed the same clean replace-in-place
behavior in reverse. `RagLiveTest` also covers this with 3 dedicated live tests
(unchanged-file no-op, changed-file replace-in-place, orphan cleanup) using real
temp-directory fixtures against the real pgvector/Ollama stack — 6/6 passing.

## Not yet built

`POST /api/knowledge/documents` (upload a new knowledge doc via the API rather than the
filesystem) / `POST /api/knowledge/search` (spec section 42) for full knowledge-base
management without touching `knowledge/` directly — `POST /api/knowledge/reingest`
(above) covers the "pick up a filesystem change live" half of this, not document
upload via the API.
