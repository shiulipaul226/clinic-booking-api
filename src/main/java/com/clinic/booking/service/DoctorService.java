package com.clinic.booking.service;

import com.clinic.booking.domain.Doctor;
import com.clinic.booking.domain.Slot;
import com.clinic.booking.domain.SlotStatus;
import com.clinic.booking.dto.DoctorRequest;
import com.clinic.booking.dto.DoctorResponse;
import com.clinic.booking.dto.GenerateSlotsRequest;
import com.clinic.booking.dto.SlotResponse;
import com.clinic.booking.exception.BusinessRuleException;
import com.clinic.booking.exception.NotFoundException;
import com.clinic.booking.repository.DoctorRepository;
import com.clinic.booking.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DoctorService {

    private final DoctorRepository doctors;
    private final SlotRepository slots;
    private final Clock clock;

    public DoctorService(DoctorRepository doctors, SlotRepository slots, Clock clock) {
        this.doctors = doctors;
        this.slots = slots;
        this.clock = clock;
    }

    @Transactional
    public DoctorResponse create(DoctorRequest request) {
        return DoctorResponse.from(doctors.save(new Doctor(request.name().trim(), request.specialization().trim())));
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> list() {
        return doctors.findAll().stream().map(DoctorResponse::from).toList();
    }

    /** Creates back-to-back slots between startTime and endTime. Slots that already exist are skipped. */
    @Transactional
    public List<SlotResponse> generateSlots(Long doctorId, GenerateSlotsRequest request) {
        Doctor doctor = doctors.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor " + doctorId + " not found"));
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessRuleException("endTime must be after startTime");
        }
        if (request.date().isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleException("Cannot create slots in the past");
        }

        LocalDateTime cursor = request.date().atTime(request.startTime());
        LocalDateTime end = request.date().atTime(request.endTime());
        List<Slot> toCreate = new ArrayList<>();
        while (!cursor.plusMinutes(request.slotMinutes()).isAfter(end)) {
            LocalDateTime slotEnd = cursor.plusMinutes(request.slotMinutes());
            if (!slots.existsByDoctorIdAndStartTime(doctorId, cursor)) {
                toCreate.add(new Slot(doctor, cursor, slotEnd));
            }
            cursor = slotEnd;
        }
        return slots.saveAll(toCreate).stream().map(SlotResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> listSlots(Long doctorId, LocalDate date, boolean onlyAvailable) {
        if (!doctors.existsById(doctorId)) {
            throw new NotFoundException("Doctor " + doctorId + " not found");
        }
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        List<Slot> result = onlyAvailable
                ? slots.findByDoctorIdAndStatusAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
                        doctorId, SlotStatus.AVAILABLE, from, to)
                : slots.findByDoctorIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
                        doctorId, from, to);
        return result.stream().map(SlotResponse::from).toList();
    }
}
