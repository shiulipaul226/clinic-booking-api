package com.clinic.booking.dto;

import com.clinic.booking.domain.Appointment;
import com.clinic.booking.domain.AppointmentStatus;
import com.clinic.booking.domain.Slot;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        AppointmentStatus status,
        Long slotId,
        Long doctorId,
        String doctorName,
        String patientName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt) {

    public static AppointmentResponse from(Appointment a) {
        Slot s = a.getSlot();
        return new AppointmentResponse(
                a.getId(),
                a.getStatus(),
                s.getId(),
                s.getDoctor().getId(),
                s.getDoctor().getName(),
                a.getPatient().getName(),
                s.getStartTime(),
                s.getEndTime(),
                a.getCreatedAt(),
                a.getCancelledAt());
    }
}
