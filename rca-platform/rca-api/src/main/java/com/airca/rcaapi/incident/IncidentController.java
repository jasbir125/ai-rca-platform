package com.airca.rcaapi.incident;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse response = incidentService.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<IncidentResponse> listIncidents() {
        return incidentService.listIncidents();
    }

    @GetMapping("/{id}")
    public IncidentResponse getIncident(@PathVariable UUID id) {
        return incidentService.getIncident(id);
    }

    @PostMapping("/{id}/investigate")
    public ResponseEntity<InvestigateResponse> investigate(@PathVariable UUID id) {
        InvestigateResponse response = incidentService.startInvestigation(id);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{id}/evidence")
    public List<EvidenceResponse> getEvidence(@PathVariable UUID id) {
        return incidentService.listEvidence(id);
    }

    @GetMapping("/{id}/hypotheses")
    public List<HypothesisResponse> getHypotheses(@PathVariable UUID id) {
        return incidentService.listHypotheses(id);
    }

    @GetMapping("/{id}/timeline")
    public List<TimelineResponse> getTimeline(@PathVariable UUID id) {
        return incidentService.listTimeline(id);
    }

    @GetMapping("/{id}/recommendations")
    public List<RecommendationResponse> getRecommendations(@PathVariable UUID id) {
        return incidentService.listRecommendations(id);
    }
}
