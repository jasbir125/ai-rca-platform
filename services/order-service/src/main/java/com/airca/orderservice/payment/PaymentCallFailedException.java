package com.airca.orderservice.payment;

public class PaymentCallFailedException extends PaymentClientException {

    public PaymentCallFailedException(String message, Throwable cause) {
        super("PAYMENT_CALL_FAILED", message, cause);
    }
}
