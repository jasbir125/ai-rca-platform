# Architecture

This describes what's actually built (the MVP golden path), not the full 64-section
spec — see `plan.md` for the backlog and the reasoning behind every deviation from the
spec's literal structure.

## Overall system

```mermaid
flowchart TB
    subgraph Observed["The observed system"]
        OrderSvc["order-service"] -->|"REST, configurable timeout"| PaymentSvc["payment-service"]
    end

    subgraph Obs["Observability (infrastructure/docker)"]
        Prom["Prometheus"]
        Loki["Loki + Promtail"]
        Jaeger["Jaeger"]
    end

    OrderSvc -.metrics.-> Prom
    PaymentSvc -.metrics.-> Prom
    OrderSvc -.logs.-> Loki
    PaymentSvc -.logs.-> Loki
    OrderSvc -.traces.-> Jaeger
    PaymentSvc -.traces.-> Jaeger

    subgraph RCA["rca-platform"]
        API["rca-api\n(REST + persistence + async trigger)"]
        Orch["InvestigationOrchestrator\n(agent-framework)"]
        Agents["MetricsAgent / LogAgent / TraceAgent"]
        RAG["RAG (pgvector)"]
        LLM["AiModelProvider\n(Ollama, local)"]
    end

    API --> Orch
    Orch --> Agents
    Orch --> RAG
    Orch --> LLM
    Agents -->|queryMetrics| Prom
    Agents -->|searchLogs| Loki
    Agents -->|searchTraces| Jaeger
    RAG --> KB[("knowledge/\narchitecture, runbooks,\nprevious RCAs")]

    API --> PG[("Postgres\nincidents, evidence,\nhypotheses, timeline")]
```

## Investigation flow

```mermaid
sequenceDiagram
    participant Client
    participant API as rca-api
    participant Runner as InvestigationRunner (async)
    participant Orch as InvestigationOrchestrator
    participant Agents as Metrics/Log/Trace Agents
    participant RAG
    participant LLM as Ollama

    Client->>API: POST /api/incidents
    API-->>Client: 201 {id, status: PENDING}
    Client->>API: POST /api/incidents/{id}/investigate
    API->>API: mark INVESTIGATING
    API-->>Client: 202 {id, status: INVESTIGATING}
    API->>Runner: run(id) [@Async]

    par
        Runner->>Orch: investigate(request)
        Orch->>Agents: run in parallel (GuardedAgentExecutor)
        Agents-->>Orch: AgentResult[] (evidence, or TIMEOUT/FAILED)
        Orch->>RAG: search(service + description)
        RAG-->>Orch: relevant knowledge chunks
        Orch->>LLM: system prompt + evidence + knowledge -> RcaReport
        LLM-->>Orch: structured RcaReport
        Orch-->>Runner: InvestigationResult
        Runner->>API: persist evidence, hypotheses, timeline, recommendations
        Runner->>API: mark COMPLETED (or FAILED with reason)
    end

    Client->>API: GET /api/incidents/{id} (poll)
    API-->>Client: status, and once COMPLETED: summary, probableRootCause, confidence
```

## RAG pipeline

```mermaid
flowchart LR
    Doc["knowledge/**/*.md\n(frontmatter + body)"] --> Parser["FrontmatterParser"]
    Parser --> Chunk["TokenTextSplitter"]
    Chunk --> Embed["Ollama embeddings\n(nomic-embed-text, 768d)"]
    Embed --> Store[("pgvector\nschema: rag")]
    Store --> Search["KnowledgeRetrievalService\n.search(query, filters)"]
    Search --> Tool["SearchKnowledgeTool\n(@Tool for the LLM)"]
    Search --> Orch["InvestigationOrchestrator\n(direct retrieval for the final RCA call)"]
```

Live operational data (Prometheus/Loki/Jaeger) and enterprise knowledge (RAG) are
deliberately separate paths that only meet inside the LLM's prompt — RAG never serves
live metrics/logs, and the tools never serve architecture docs. This is the spec's
"LIVE DATA + RAG CONTEXT + AGENT TOOL RESULTS + LLM REASONING = EVIDENCE-BASED RCA"
principle (section 63), and it's why the knowledge search in the final reasoning step
is unfiltered by service — the Payment runbook is relevant evidence for an
order-service incident, and filtering it out by service would have hidden it.

## Agent guardrails

Every agent declares, in code, exactly what it's allowed to do:

```mermaid
flowchart TB
    Agent["Agent interface\nname(), allowedTools(), maxExecutionTime(), maxToolCalls()"]
    Agent --> Guard["AgentGuardrails.assertToolAllowed\n(rejects any tool not in the allow-list)"]
    Agent --> Budget["ToolCallBudget\n(throws once maxToolCalls is exceeded)"]
    Agent --> Exec["GuardedAgentExecutor\n(enforces maxExecutionTime; a slow or\nthrowing agent returns TIMEOUT/FAILED,\nnever hangs or crashes the investigation)"]
```

`MetricsAgent`/`LogAgent`/`TraceAgent` call their tool(s) deterministically rather than
through their own LLM tool-calling loop — see `plan.md`'s Phase 6 entry for why. The
one LLM call in the whole flow is `InvestigationOrchestrator`'s final reasoning step,
which does use real Spring AI tool-calling for `SearchKnowledgeTool` where relevant
(and is verified live in `agent-framework`'s tests to actually invoke tools rather than
hallucinate answers).

## Deliberate deviations from the literal spec structure

1. **2 processes, not ~20 modules.** Section 5's folder tree implies one Maven module
   per agent/tool/concern. Agents and tools live as packages inside `agent-framework`
   instead — see `plan.md`'s "Folder structure note".
2. **rca-api and rca-orchestrator combined for the MVP**, using an in-process `@Async`
   call instead of Kafka for the investigate trigger (spec section 44 explicitly allows
   "Kafka or another suitable asynchronous mechanism"). The orchestration logic lives
   in `agent-framework` independent of both, so splitting it out later is additive.
3. **Agents call tools deterministically**, not through their own LLM loop — the
   agentic reasoning happens once, at the orchestrator level, over all agents' combined
   evidence. See `plan.md` Phase 6.
