package com.clinic.booking.exception;

/** The request is valid but clashes with the current state (e.g. slot already booked). Maps to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
