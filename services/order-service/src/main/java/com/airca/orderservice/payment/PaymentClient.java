package com.airca.orderservice.payment;

import com.airca.orderservice.chaos.ChaosState;
import com.airca.orderservice.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Calls Payment Service with a timeout that is deliberately live-mutable via
 * {@link ChaosState} rather than fixed at startup, so the incident simulator can
 * reproduce a bad-deployment-style timeout regression without restarting the process.
 */
@Component
public class PaymentClient {

    private final RestClient.Builder restClientBuilder;
    private final String paymentServiceBaseUrl;
    private final ChaosState chaosState;

    public PaymentClient(
            RestClient.Builder restClientBuilder,
            @Value("${payment.service.url}") String paymentServiceBaseUrl,
            ChaosState chaosState) {
        this.restClientBuilder = restClientBuilder;
        this.paymentServiceBaseUrl = paymentServiceBaseUrl;
        this.chaosState = chaosState;
    }

    public PaymentClientResponse charge(String orderId, BigDecimal amount) {
        long timeoutMs = chaosState.paymentCallTimeoutMs();
        RestClient restClient = buildRestClient(timeoutMs);

        try {
            PaymentClientResponse response = restClient.post()
                    .uri("/api/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentChargeRequest(orderId, amount))
                    .retrieve()
                    .body(PaymentClientResponse.class);
            if (response == null) {
                throw new PaymentCallFailedException("Payment Service returned an empty response", null);
            }
            return response;
        } catch (ResourceAccessException e) {
            if (isTimeout(e.getCause())) {
                throw new PaymentTimeoutException(timeoutMs, e);
            }
            throw new PaymentCallFailedException("Payment Service unreachable: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new PaymentCallFailedException("Payment Service call failed: " + e.getMessage(), e);
        }
    }

    /**
     * The JDK HttpClient's per-request timeout can surface as a plain
     * {@link HttpTimeoutException}, or (via Spring's JdkClientHttpRequestFactory
     * wrapping) as a generic {@link java.io.InterruptedIOException} whose message says
     * "Request timed out" rather than preserving the original exception type, so both
     * shapes are treated as a timeout here.
     */
    private boolean isTimeout(Throwable cause) {
        if (cause == null) {
            return false;
        }
        if (cause instanceof HttpTimeoutException) {
            return true;
        }
        String message = cause.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }

    private RestClient buildRestClient(long timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return restClientBuilder.clone()
                .baseUrl(paymentServiceBaseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
