package com.clinic.booking.dto;

/** Outcome of a booking call. {@code replayed} is true when an Idempotency-Key matched an earlier request. */
public record BookingResult(AppointmentResponse appointment, boolean replayed) {
}
