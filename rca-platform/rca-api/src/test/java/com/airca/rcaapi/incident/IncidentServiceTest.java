package com.airca.rcaapi.incident;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentServiceTest {

    private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
    private final IncidentEvidenceRepository evidenceRepository = mock(IncidentEvidenceRepository.class);
    private final IncidentHypothesisRepository hypothesisRepository = mock(IncidentHypothesisRepository.class);
    private final IncidentTimelineRepository timelineRepository = mock(IncidentTimelineRepository.class);
    private final IncidentRecommendationRepository recommendationRepository = mock(IncidentRecommendationRepository.class);
    private final InvestigationTrigger investigationRunner = mock(InvestigationTrigger.class);

    private final IncidentService service = new IncidentService(
            incidentRepository, evidenceRepository, hypothesisRepository, timelineRepository,
            recommendationRepository, investigationRunner);

    @Test
    void createIncidentDefaultsWindowToLast15MinutesWhenNoStartTimeGiven() {
        when(incidentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncidentResponse response = service.createIncident(
                new CreateIncidentRequest("order-service", "production", "HIGH", "Order failures", null));

        assertThat(response.service()).isEqualTo("order-service");
        assertThat(response.status()).isEqualTo(IncidentStatus.PENDING);
        assertThat(response.windowStart()).isBefore(response.windowEnd());
    }

    @Test
    void startInvestigationMarksIncidentInvestigatingAndTriggersTheRunnerAsynchronously() {
        UUID id = UUID.randomUUID();
        IncidentEntity incident = new IncidentEntity(id, "order-service", "production", "HIGH", "desc",
                Instant.now().minusSeconds(900), Instant.now(), "corr-1");
        when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvestigateResponse response = service.startInvestigation(id);

        assertThat(response.status()).isEqualTo(IncidentStatus.INVESTIGATING);
        verify(investigationRunner).run(id);
    }

    @Test
    void getIncidentThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(incidentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getIncident(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void listEvidenceThrowsWhenIncidentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(incidentRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.listEvidence(id)).isInstanceOf(NoSuchElementException.class);
    }
}
