package com.clinic.booking.exception;

/** A business rule was violated (e.g. cancelling too late). Maps to HTTP 422. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
