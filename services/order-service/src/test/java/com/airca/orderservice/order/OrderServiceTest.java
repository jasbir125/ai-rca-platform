package com.airca.orderservice.order;

import com.airca.orderservice.chaos.ChaosState;
import com.airca.orderservice.payment.PaymentClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the real Order -> Payment HTTP call path (including a real socket-level
 * timeout) against an embedded WireMock server standing in for Payment Service.
 * OrderRepository is mocked since persistence is verified live against the real
 * docker-compose Postgres instead of Testcontainers (see pom.xml for why).
 */
class OrderServiceTest {

    static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    private OrderService newOrderService(long paymentTimeoutMs) {
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChaosState chaosState = new ChaosState(paymentTimeoutMs, false);
        PaymentClient paymentClient = new PaymentClient(
                RestClient.builder(), "http://localhost:" + wireMockServer.port(), chaosState);

        return new OrderService(repository, paymentClient, chaosState, new SimpleMeterRegistry());
    }

    @Test
    void confirmsOrderWhenPaymentSucceeds() {
        wireMockServer.stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"paymentId":"pay-1","orderId":"o","amount":19.99,"status":"SUCCESS","processedAt":"2026-01-01T00:00:00Z"}
                                """)));

        OrderResponse response = newOrderService(3000).createOrder(new OrderRequest("widget", new BigDecimal("19.99")));

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.paymentId()).isEqualTo("pay-1");
        assertThat(response.failureReason()).isNull();
    }

    @Test
    void marksOrderFailedWithPaymentTimeoutWhenPaymentIsSlowerThanTimeout() {
        // Mirrors the primary demo scenario: Payment responds in ~700ms, Order's
        // timeout has been reduced (via the chaos endpoint, standing in for a bad
        // deploy) to 300ms, so the call must time out.
        wireMockServer.stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse().withFixedDelay(700).withStatus(201)));

        OrderResponse response = newOrderService(300).createOrder(new OrderRequest("widget", BigDecimal.TEN));

        assertThat(response.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("PAYMENT_TIMEOUT");
    }

    @Test
    void marksOrderFailedWhenPaymentServiceReturns500() {
        wireMockServer.stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse().withStatus(500)));

        OrderResponse response = newOrderService(3000).createOrder(new OrderRequest("widget", BigDecimal.ONE));

        assertThat(response.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("PAYMENT_CALL_FAILED");
    }

    @Test
    void simulateBugCausesARealUnhandledNullPointerExceptionForAnUnmappedItem() {
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ChaosState chaosState = new ChaosState(3000, true);
        PaymentClient paymentClient = new PaymentClient(
                RestClient.builder(), "http://localhost:" + wireMockServer.port(), chaosState);
        OrderService orderService = new OrderService(repository, paymentClient, chaosState, new SimpleMeterRegistry());

        assertThatThrownBy(() -> orderService.createOrder(new OrderRequest("widget-1", BigDecimal.ONE)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void throwsWhenOrderNotFound() {
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.findById(any())).thenReturn(Optional.empty());
        ChaosState chaosState = new ChaosState(100, false);
        OrderService orderService = new OrderService(
                repository,
                new PaymentClient(RestClient.builder(), "http://localhost:1", chaosState),
                chaosState,
                new SimpleMeterRegistry());

        assertThatThrownBy(() -> orderService.getOrder(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
