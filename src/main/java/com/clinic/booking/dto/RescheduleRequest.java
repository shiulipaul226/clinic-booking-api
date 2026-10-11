package com.clinic.booking.dto;

import jakarta.validation.constraints.NotNull;

public record RescheduleRequest(@NotNull Long newSlotId) {
}
