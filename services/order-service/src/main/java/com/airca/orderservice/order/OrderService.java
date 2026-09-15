package com.airca.orderservice.order;

import com.airca.orderservice.chaos.ChaosState;
import com.airca.orderservice.payment.PaymentClient;
import com.airca.orderservice.payment.PaymentClientException;
import com.airca.orderservice.payment.PaymentClientResponse;
import com.airca.orderservice.payment.PaymentTimeoutException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Loyalty-tier lookup used to apply a bulk discount. Deliberately incomplete —
     *  see the {@code chaos.simulate-bug} knob below. */
    private static final Map<String, String> LOYALTY_TIERS = Map.of(
            "premium-widget", "GOLD",
            "standard-widget", "SILVER");

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final ChaosState chaosState;
    private final Counter ordersConfirmedCounter;
    private final Counter ordersFailedCounter;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient, ChaosState chaosState,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.chaosState = chaosState;
        this.ordersConfirmedCounter = Counter.builder("orders_processed_total")
                .tag("status", "confirmed")
                .register(meterRegistry);
        this.ordersFailedCounter = Counter.builder("orders_processed_total")
                .tag("status", "failed")
                .register(meterRegistry);
    }

    public OrderResponse createOrder(OrderRequest request) {
        Order order = new Order(UUID.randomUUID(), request.itemDescription(), request.amount());
        orderRepository.save(order);

        if (chaosState.simulateBug()) {
            applyLoyaltyDiscount(order);
        }

        try {
            PaymentClientResponse paymentResponse = paymentClient.charge(order.getId().toString(), order.getAmount());
            order.markConfirmed(paymentResponse.paymentId());
            ordersConfirmedCounter.increment();
            log.info("order_confirmed orderId={} paymentId={}", order.getId(), paymentResponse.paymentId());
        } catch (PaymentTimeoutException e) {
            order.markFailed(e.getErrorType());
            ordersFailedCounter.increment();
            log.error(
                    "order_failed orderId={} errorType={} httpStatus=500 reason=\"{}\"",
                    order.getId(), e.getErrorType(), e.getMessage());
        } catch (PaymentClientException e) {
            order.markFailed(e.getErrorType());
            ordersFailedCounter.increment();
            log.error(
                    "order_failed orderId={} errorType={} httpStatus=502 reason=\"{}\"",
                    order.getId(), e.getErrorType(), e.getMessage());
        }

        orderRepository.save(order);
        return OrderResponse.from(order);
    }

    /** Real, unhandled defect: {@code LOYALTY_TIERS} isn't populated for most catalog
     *  items, so {@code tier} is null here for any item outside the two hardcoded
     *  entries above — the {@code .equals()} call throws a genuine
     *  {@link NullPointerException} that propagates uncaught (no handler for it in
     *  {@link com.airca.orderservice.error.GlobalExceptionHandler}), a different
     *  failure archetype from the timeout/latency chaos scenarios: an application code
     *  bug, not a config or dependency issue. */
    private void applyLoyaltyDiscount(Order order) {
        String tier = LOYALTY_TIERS.get(order.getItemDescription());
        log.info("applying_loyalty_discount orderId={} item={} tier={}", order.getId(), order.getItemDescription(), tier);
        if (tier.equals("GOLD")) {
            log.info("gold_tier_discount_applied orderId={}", order.getId());
        }
    }

    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + id));
        return OrderResponse.from(order);
    }

    public java.util.List<OrderResponse> listOrders() {
        return orderRepository.findAll().stream().map(OrderResponse::from).toList();
    }
}
