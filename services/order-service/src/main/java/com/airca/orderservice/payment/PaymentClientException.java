package com.airca.orderservice.payment;

public class PaymentClientException extends RuntimeException {

    private final String errorType;

    public PaymentClientException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public PaymentClientException(String errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
