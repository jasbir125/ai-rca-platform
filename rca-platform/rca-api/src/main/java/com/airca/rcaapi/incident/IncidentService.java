package com.airca.rcaapi.incident;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class IncidentService {

    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final IncidentHypothesisRepository hypothesisRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final IncidentRecommendationRepository recommendationRepository;
    private final InvestigationTrigger investigationRunner;

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentEvidenceRepository evidenceRepository,
            IncidentHypothesisRepository hypothesisRepository,
            IncidentTimelineRepository timelineRepository,
            IncidentRecommendationRepository recommendationRepository,
            InvestigationTrigger investigationRunner) {
        this.incidentRepository = incidentRepository;
        this.evidenceRepository = evidenceRepository;
        this.hypothesisRepository = hypothesisRepository;
        this.timelineRepository = timelineRepository;
        this.recommendationRepository = recommendationRepository;
        this.investigationRunner = investigationRunner;
    }

    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Instant windowEnd = Instant.now();
        Instant windowStart = request.startTime() != null ? request.startTime() : windowEnd.minus(DEFAULT_WINDOW);

        IncidentEntity incident = new IncidentEntity(
                UUID.randomUUID(), request.service(), request.environment(), request.severity(),
                request.description(), windowStart, windowEnd, UUID.randomUUID().toString());
        incidentRepository.save(incident);
        return IncidentResponse.from(incident);
    }

    public InvestigateResponse startInvestigation(UUID incidentId) {
        IncidentEntity incident = getEntity(incidentId);
        incident.markInvestigating();
        incidentRepository.save(incident);

        investigationRunner.run(incidentId);

        return new InvestigateResponse(incidentId, incident.getStatus());
    }

    public IncidentResponse getIncident(UUID incidentId) {
        return IncidentResponse.from(getEntity(incidentId));
    }

    public List<IncidentResponse> listIncidents() {
        return incidentRepository.findAll().stream().map(IncidentResponse::from).toList();
    }

    public List<EvidenceResponse> listEvidence(UUID incidentId) {
        requireExists(incidentId);
        return evidenceRepository.findByIncidentIdOrderByCapturedAtAsc(incidentId).stream()
                .map(EvidenceResponse::from).toList();
    }

    public List<HypothesisResponse> listHypotheses(UUID incidentId) {
        requireExists(incidentId);
        return hypothesisRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(HypothesisResponse::from).toList();
    }

    public List<TimelineResponse> listTimeline(UUID incidentId) {
        requireExists(incidentId);
        return timelineRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(TimelineResponse::from).toList();
    }

    public List<RecommendationResponse> listRecommendations(UUID incidentId) {
        requireExists(incidentId);
        return recommendationRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(RecommendationResponse::from).toList();
    }

    private IncidentEntity getEntity(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));
    }

    private void requireExists(UUID incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new NoSuchElementException("Incident not found: " + incidentId);
        }
    }
}
