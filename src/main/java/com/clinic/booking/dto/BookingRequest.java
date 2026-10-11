package com.clinic.booking.dto;

import jakarta.validation.constraints.NotNull;

public record BookingRequest(@NotNull Long slotId) {
}
