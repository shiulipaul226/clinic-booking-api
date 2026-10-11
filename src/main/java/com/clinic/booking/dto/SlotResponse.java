package com.clinic.booking.dto;

import com.clinic.booking.domain.Slot;
import com.clinic.booking.domain.SlotStatus;

import java.time.LocalDateTime;

public record SlotResponse(Long id, Long doctorId, LocalDateTime startTime, LocalDateTime endTime, SlotStatus status) {

    public static SlotResponse from(Slot s) {
        return new SlotResponse(s.getId(), s.getDoctor().getId(), s.getStartTime(), s.getEndTime(), s.getStatus());
    }
}
