package com.airca.orderservice.payment;

public class PaymentTimeoutException extends PaymentClientException {

    public PaymentTimeoutException(long timeoutMs, Throwable cause) {
        super("PAYMENT_TIMEOUT", "Payment Service call timed out after " + timeoutMs + "ms", cause);
    }
}
