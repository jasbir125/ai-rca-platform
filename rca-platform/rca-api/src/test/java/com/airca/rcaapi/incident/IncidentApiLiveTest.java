package com.airca.rcaapi.incident;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full product surface, end to end: real REST API, real Postgres persistence, real
 * agents/RAG/LLM underneath, driven exactly the way an operator (or the future UI)
 * would use it — create an incident, trigger investigation, poll until done, read back
 * the RCA report. Requires `docker compose up -d` from infrastructure/docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IncidentApiLiveTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void reproduceThePrimaryDemoScenario() throws Exception {
        post("http://localhost:8081/admin/chaos", "{\"paymentTimeoutMs\":3000}");
        post("http://localhost:8081/api/orders", "{\"itemDescription\":\"widget\",\"amount\":9.99}");

        post("http://localhost:8081/admin/chaos", "{\"paymentTimeoutMs\":500}");
        for (int i = 0; i < 3; i++) {
            post("http://localhost:8081/api/orders", "{\"itemDescription\":\"widget\",\"amount\":9.99}");
        }
        Thread.sleep(2000);
        post("http://localhost:8081/admin/chaos", "{\"paymentTimeoutMs\":3000}");
    }

    private static void post(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400 && response.statusCode() != 500) {
            throw new IllegalStateException("Unexpected status " + response.statusCode() + " from " + url);
        }
    }

    @Test
    void createsInvestigatesAndCompletesAnIncidentThroughTheRealApi() {
        CreateIncidentRequest createRequest = new CreateIncidentRequest(
                "order-service", "local", "HIGH",
                "Order creation is failing. Customers report checkout errors.",
                Instant.now().minus(Duration.ofMinutes(10)));

        ResponseEntity<IncidentResponse> createResponse = restTemplate.postForEntity(
                "/api/incidents", createRequest, IncidentResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        IncidentResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo(IncidentStatus.PENDING);

        ResponseEntity<InvestigateResponse> investigateResponse = restTemplate.postForEntity(
                "/api/incidents/{id}/investigate", null, InvestigateResponse.class, created.id());
        assertThat(investigateResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(investigateResponse.getBody()).isNotNull();
        assertThat(investigateResponse.getBody().status()).isEqualTo(IncidentStatus.INVESTIGATING);

        IncidentResponse finalState = pollUntilTerminal(created.id());

        assertThat(finalState.status()).isEqualTo(IncidentStatus.COMPLETED)
                .as("incident should reach COMPLETED, was: %s (failureReason=%s)",
                        finalState.status(), finalState.failureReason());
        assertThat(finalState.probableRootCause()).isNotBlank();
        assertThat(finalState.confidence()).isBetween(0.0, 1.0);

        ResponseEntity<EvidenceResponse[]> evidenceResponse = restTemplate.getForEntity(
                "/api/incidents/{id}/evidence", EvidenceResponse[].class, created.id());
        assertThat(evidenceResponse.getBody()).isNotEmpty();

        ResponseEntity<HypothesisResponse[]> hypothesesResponse = restTemplate.getForEntity(
                "/api/incidents/{id}/hypotheses", HypothesisResponse[].class, created.id());
        assertThat(hypothesesResponse.getBody()).isNotEmpty();

        ResponseEntity<AgentExecutionResponse[]> agentStatusResponse = restTemplate.getForEntity(
                "/api/agents/status", AgentExecutionResponse[].class);
        assertThat(agentStatusResponse.getBody()).isNotEmpty();
        assertThat(agentStatusResponse.getBody())
                .anySatisfy(a -> assertThat(a.incidentId()).isEqualTo(created.id()));
    }

    private IncidentResponse pollUntilTerminal(java.util.UUID incidentId) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(8));
        while (Instant.now().isBefore(deadline)) {
            IncidentResponse response = restTemplate.getForObject("/api/incidents/{id}", IncidentResponse.class, incidentId);
            if (response.status() == IncidentStatus.COMPLETED || response.status() == IncidentStatus.FAILED) {
                return response;
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("Incident did not reach a terminal state within the deadline");
    }
}
