package com.airca.rcaapi.incident;

import com.airca.rca.framework.agent.AgentResult;
import com.airca.rca.framework.evidence.Evidence;
import com.airca.rca.framework.orchestrator.InvestigationOrchestrator;
import com.airca.rca.framework.orchestrator.InvestigationRequest;
import com.airca.rca.framework.orchestrator.InvestigationResult;
import com.airca.rca.framework.orchestrator.RcaReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Runs one investigation on the {@code investigationExecutor} pool and persists
 * everything it produces, whether or not the LLM reasoning step itself succeeded (spec
 * section 27/29: a failed investigation still records what was checked). Kept as its
 * own bean, separate from {@link IncidentService}, so {@code @Async} is invoked through
 * Spring's proxy rather than via same-class self-invocation (which @Async silently
 * ignores).
 */
@Component
public class InvestigationRunner implements InvestigationTrigger {

    private static final Logger log = LoggerFactory.getLogger(InvestigationRunner.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final IncidentHypothesisRepository hypothesisRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final IncidentRecommendationRepository recommendationRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final InvestigationOrchestrator orchestrator;

    public InvestigationRunner(
            IncidentRepository incidentRepository,
            IncidentEvidenceRepository evidenceRepository,
            IncidentHypothesisRepository hypothesisRepository,
            IncidentTimelineRepository timelineRepository,
            IncidentRecommendationRepository recommendationRepository,
            AgentExecutionRepository agentExecutionRepository,
            InvestigationOrchestrator orchestrator) {
        this.incidentRepository = incidentRepository;
        this.evidenceRepository = evidenceRepository;
        this.hypothesisRepository = hypothesisRepository;
        this.timelineRepository = timelineRepository;
        this.recommendationRepository = recommendationRepository;
        this.agentExecutionRepository = agentExecutionRepository;
        this.orchestrator = orchestrator;
    }

    @Override
    @Async("investigationExecutor")
    public void run(UUID incidentId) {
        IncidentEntity incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));

        log.info("investigation_started incidentId={} service={}", incidentId, incident.getService());

        InvestigationRequest request = new InvestigationRequest(
                incidentId.toString(), incident.getService(), incident.getEnvironment(), incident.getSeverity(),
                incident.getDescription(), incident.getWindowStart(), incident.getWindowEnd(),
                incident.getCorrelationId());

        InvestigationResult result;
        try {
            result = orchestrator.investigate(request);
        } catch (Exception e) {
            log.error("investigation_orchestrator_failed incidentId={} reason={}", incidentId, e.getMessage(), e);
            incident.markFailed("Investigation could not complete: " + e.getMessage());
            incidentRepository.save(incident);
            return;
        }

        persist(incidentId, result);

        if (result.succeeded()) {
            RcaReport report = result.rcaReport();
            RcaReport.Impact impact = report.impact();
            incident.markCompleted(report.summary(), report.probableRootCause(), report.confidence(),
                    report.requiresHumanApproval(),
                    impact == null ? null : impact.services(),
                    impact == null ? null : impact.businessFlows(),
                    impact == null ? null : impact.estimatedImpact(),
                    report.similarIncidents(), report.nextActions());
            log.info("investigation_completed incidentId={} confidence={} requiresApproval={}",
                    incidentId, report.confidence(), report.requiresHumanApproval());
        } else {
            incident.markFailed(result.failureReason());
            log.warn("investigation_failed incidentId={} reason={}", incidentId, result.failureReason());
        }
        incidentRepository.save(incident);
    }

    private void persist(UUID incidentId, InvestigationResult result) {
        for (AgentResult agentResult : result.agentResults()) {
            agentExecutionRepository.save(new AgentExecutionEntity(
                    UUID.randomUUID(), incidentId, agentResult.agentName(), agentResult.status().name(),
                    agentResult.durationMs(), String.join(",", agentResult.toolsUsed()),
                    agentResult.summary(), agentResult.errorMessage()));
        }

        List<Evidence> allEvidence = result.agentResults().stream()
                .flatMap(r -> r.evidence().stream())
                .toList();
        for (Evidence evidence : allEvidence) {
            evidenceRepository.save(toEntity(incidentId, evidence));
        }
        for (Evidence evidence : result.ragEvidence()) {
            evidenceRepository.save(toEntity(incidentId, evidence));
        }

        if (result.succeeded()) {
            RcaReport report = result.rcaReport();
            for (RcaReport.Hypothesis h : report.hypotheses()) {
                hypothesisRepository.save(new IncidentHypothesisEntity(
                        UUID.randomUUID(), incidentId, h.description(), h.probability(), h.evidenceSummary()));
            }
            for (RcaReport.TimelineEntry t : report.timeline()) {
                timelineRepository.save(new IncidentTimelineEntity(UUID.randomUUID(), incidentId, t.timestamp(), t.event()));
            }
            for (String recommendation : report.recommendations()) {
                recommendationRepository.save(new IncidentRecommendationEntity(UUID.randomUUID(), incidentId, recommendation));
            }
        }
    }

    private IncidentEvidenceEntity toEntity(UUID incidentId, Evidence evidence) {
        return new IncidentEvidenceEntity(
                UUID.randomUUID(), incidentId, evidence.source(), evidence.service(),
                evidence.type().name(), evidence.description(), evidence.value(),
                evidence.confidence().name(), evidence.correlationId(), evidence.reference(),
                evidence.timestamp());
    }
}
