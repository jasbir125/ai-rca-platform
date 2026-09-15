package com.airca.rca.framework.orchestrator;

import com.airca.rca.framework.agent.Agent;
import com.airca.rca.framework.agent.AgentContext;
import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.agent.GuardedAgentExecutor;
import com.airca.rca.framework.agents.LogAgent;
import com.airca.rca.framework.agents.MetricsAgent;
import com.airca.rca.framework.agents.TraceAgent;
import com.airca.rca.framework.ai.AiModelProvider;
import com.airca.rca.framework.evidence.ConfidenceLevel;
import com.airca.rca.framework.evidence.Evidence;
import com.airca.rca.framework.evidence.EvidenceType;
import com.airca.rca.framework.prompt.PromptLoader;
import com.airca.rca.framework.rag.KnowledgeRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The core investigation flow (spec section 13, compressed to what's actually built):
 * run the specialized agents to gather live evidence, retrieve enterprise knowledge via
 * RAG, then make exactly one structured-output LLM call to reason over both and
 * produce an {@link RcaReport}. Deliberately its own class independent of any REST/Web
 * concerns so it can be driven from an in-process async call today (rca-api) or a
 * Kafka consumer later (rca-orchestrator) without changing this logic — see plan.md.
 */
@Component
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);
    // Kept deliberately small: this platform's default local model runs with a modest
    // context window, and spec section 46 explicitly calls for not sending more to the
    // LLM than necessary ("never send 1 million logs... summarize... context limits").
    private static final int MAX_EVIDENCE_VALUE_CHARS = 800;

    private final MetricsAgent metricsAgent;
    private final LogAgent logAgent;
    private final TraceAgent traceAgent;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final AiModelProvider aiModelProvider;
    private final PromptLoader promptLoader;
    private final GuardedAgentExecutor guardedAgentExecutor;

    public InvestigationOrchestrator(
            MetricsAgent metricsAgent,
            LogAgent logAgent,
            TraceAgent traceAgent,
            KnowledgeRetrievalService knowledgeRetrievalService,
            AiModelProvider aiModelProvider,
            PromptLoader promptLoader) {
        this.metricsAgent = metricsAgent;
        this.logAgent = logAgent;
        this.traceAgent = traceAgent;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.aiModelProvider = aiModelProvider;
        this.promptLoader = promptLoader;
        this.guardedAgentExecutor = new GuardedAgentExecutor();
    }

    public InvestigationResult investigate(InvestigationRequest request) {
        AgentContext context = new AgentContext(
                request.incidentId(), request.service(), request.environment(),
                request.windowStart(), request.windowEnd(), request.correlationId(), request.description());

        List<AgentResult> agentResults = runAgentsInParallel(context);

        List<Evidence> agentEvidence = agentResults.stream()
                .flatMap(r -> r.evidence().stream())
                .toList();

        List<Evidence> ragEvidence = retrieveKnowledge(request);

        try {
            RcaReport report = reason(request, agentEvidence, ragEvidence);
            return InvestigationResult.success(agentResults, ragEvidence, report);
        } catch (Exception e) {
            log.error("investigation_llm_reasoning_failed incidentId={} reason={}", request.incidentId(), e.getMessage());
            return InvestigationResult.failure(agentResults, ragEvidence,
                    "LLM reasoning step failed: " + e.getMessage());
        }
    }

    private List<AgentResult> runAgentsInParallel(AgentContext context) {
        List<Agent> agents = List.of(metricsAgent, logAgent, traceAgent);
        List<CompletableFuture<AgentResult>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(() -> guardedAgentExecutor.execute(agent, context)))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private List<Evidence> retrieveKnowledge(InvestigationRequest request) {
        try {
            List<Document> documents = knowledgeRetrievalService.search(
                    request.service() + " " + request.description(), Map.of());
            return documents.stream()
                    .map(doc -> Evidence.builder()
                            .source("RAG")
                            .service(String.valueOf(doc.getMetadata().getOrDefault("service", request.service())))
                            .type(EvidenceType.KNOWLEDGE)
                            .description(String.valueOf(doc.getMetadata().getOrDefault("documentType", "KNOWLEDGE")))
                            .value(truncate(doc.getText()))
                            .confidence(ConfidenceLevel.CONFIRMED)
                            .reference(String.valueOf(doc.getMetadata().getOrDefault("source", "unknown")))
                            .build())
                    .toList();
        } catch (RuntimeException e) {
            log.warn("rag_retrieval_failed incidentId={} reason={}", request.incidentId(), e.getMessage());
            return List.of();
        }
    }

    private RcaReport reason(InvestigationRequest request, List<Evidence> agentEvidence, List<Evidence> ragEvidence) {
        String evidenceBlock = agentEvidence.isEmpty()
                ? "No live evidence was gathered."
                : agentEvidence.stream().map(this::formatEvidence).reduce("", (a, b) -> a + b + "\n");

        String knowledgeBlock = ragEvidence.isEmpty()
                ? "No relevant enterprise knowledge was retrieved."
                : ragEvidence.stream().map(this::formatEvidence).reduce("", (a, b) -> a + b + "\n");

        String systemPrompt = promptLoader.load("system-prompt");
        String userPrompt = promptLoader.load("final-rca-prompt", Map.of(
                "incidentId", request.incidentId(),
                "service", request.service(),
                "environment", request.environment(),
                "severity", request.severity() == null ? "UNKNOWN" : request.severity(),
                "description", request.description(),
                "windowStart", String.valueOf(request.windowStart()),
                "windowEnd", String.valueOf(request.windowEnd()),
                "evidenceBlock", evidenceBlock,
                "knowledgeBlock", knowledgeBlock));

        return aiModelProvider.chatClient()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(RcaReport.class);
    }

    private String formatEvidence(Evidence evidence) {
        return "[%s] service=%s type=%s confidence=%s: %s | %s".formatted(
                evidence.source(), evidence.service(), evidence.type(), evidence.confidence(),
                evidence.description(), truncate(evidence.value()));
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_EVIDENCE_VALUE_CHARS ? value.substring(0, MAX_EVIDENCE_VALUE_CHARS) + "...[truncated]" : value;
    }
}
