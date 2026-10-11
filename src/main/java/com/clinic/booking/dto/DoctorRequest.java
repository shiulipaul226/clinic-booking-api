package com.clinic.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DoctorRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String specialization) {
}
