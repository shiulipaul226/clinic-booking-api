package com.clinic.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** Generates back-to-back slots of {@code slotMinutes} between startTime and endTime on one date. */
public record GenerateSlotsRequest(
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(10) @Max(240) int slotMinutes) {
}
