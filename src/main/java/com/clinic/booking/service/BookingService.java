package com.clinic.booking.service;

import com.clinic.booking.domain.Appointment;
import com.clinic.booking.domain.AppointmentStatus;
import com.clinic.booking.domain.Slot;
import com.clinic.booking.domain.User;
import com.clinic.booking.dto.AppointmentResponse;
import com.clinic.booking.dto.BookingResult;
import com.clinic.booking.exception.BusinessRuleException;
import com.clinic.booking.exception.ConflictException;
import com.clinic.booking.exception.NotFoundException;
import com.clinic.booking.repository.AppointmentRepository;
import com.clinic.booking.repository.SlotRepository;
import com.clinic.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final UserRepository users;
    private final Clock clock;
    private final int cancellationWindowHours;

    public BookingService(AppointmentRepository appointments,
                          SlotRepository slots,
                          UserRepository users,
                          Clock clock,
                          @Value("${clinic.cancellation-window-hours}") int cancellationWindowHours) {
        this.appointments = appointments;
        this.slots = slots;
        this.users = users;
        this.clock = clock;
        this.cancellationWindowHours = cancellationWindowHours;
    }

    /**
     * Books a slot for a patient.
     *
     * <ul>
     *   <li>Idempotency: if the same patient already used this Idempotency-Key, the original appointment
     *       is returned (replayed = true) instead of booking again, so client/network retries are safe.</li>
     *   <li>Concurrency: the slot is claimed with an atomic compare-and-set in the database. Of any number
     *       of simultaneous requests for one slot, exactly one wins; the others get a ConflictException.</li>
     * </ul>
     */
    @Transactional
    public BookingResult book(Long patientId, Long slotId, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Optional<Appointment> existing = appointments.findByPatientIdAndIdempotencyKey(patientId, key);
            if (existing.isPresent()) {
                return new BookingResult(AppointmentResponse.from(existing.get()), true);
            }
        }

        Slot slot = slots.findById(slotId)
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " not found"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!slot.getStartTime().isAfter(now)) {
            throw new BusinessRuleException("Cannot book a slot that has already started");
        }

        claimSlot(slotId);

        User patient = users.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient " + patientId + " not found"));
        Appointment appointment = appointments.save(new Appointment(slot, patient, key, now));
        return new BookingResult(AppointmentResponse.from(appointment), false);
    }

    /** Cancelling is idempotent: cancelling an already-cancelled appointment just returns it. */
    @Transactional
    public AppointmentResponse cancel(Long patientId, Long appointmentId) {
        Appointment appointment = findOwned(patientId, appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return AppointmentResponse.from(appointment);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        enforceCancellationWindow(appointment, now);
        cancelAndRelease(appointment, now);
        return AppointmentResponse.from(appointment);
    }

    /**
     * Moves an appointment to another slot of the SAME doctor, atomically in one transaction:
     * the new slot is claimed first, and only then is the old appointment cancelled. If the new slot
     * was taken (409) nothing changes and the patient keeps their original appointment.
     */
    @Transactional
    public AppointmentResponse reschedule(Long patientId, Long appointmentId, Long newSlotId) {
        Appointment old = findOwned(patientId, appointmentId);
        if (old.getStatus() != AppointmentStatus.BOOKED) {
            throw new BusinessRuleException("Only booked appointments can be rescheduled");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        enforceCancellationWindow(old, now);

        if (old.getSlot().getId().equals(newSlotId)) {
            throw new BusinessRuleException("Appointment is already in this slot");
        }
        Slot target = slots.findById(newSlotId)
                .orElseThrow(() -> new NotFoundException("Slot " + newSlotId + " not found"));
        if (!target.getDoctor().getId().equals(old.getSlot().getDoctor().getId())) {
            throw new BusinessRuleException("Rescheduling is only allowed with the same doctor");
        }
        if (!target.getStartTime().isAfter(now)) {
            throw new BusinessRuleException("Cannot reschedule into a slot that has already started");
        }

        claimSlot(newSlotId);
        cancelAndRelease(old, now);
        Appointment replacement = appointments.save(new Appointment(target, old.getPatient(), null, now));
        return AppointmentResponse.from(replacement);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myAppointments(Long patientId) {
        return appointments.findDetailedByPatient(patientId).stream().map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> allAppointments() {
        return appointments.findAllDetailed().stream().map(AppointmentResponse::from).toList();
    }

    // ---------------------------------------------------------------------------------------------

    /** Atomically flips the slot AVAILABLE -> BOOKED. Throws ConflictException if someone else got it first. */
    private void claimSlot(Long slotId) {
        int updated;
        try {
            updated = slots.markBooked(slotId);
        } catch (ConcurrencyFailureException e) {
            // Some databases report the lost race as a lock failure instead of "0 rows updated".
            throw new ConflictException("Slot is being booked by someone else, please try again");
        }
        if (updated == 0) {
            throw new ConflictException("Slot is no longer available");
        }
    }

    private void cancelAndRelease(Appointment appointment, LocalDateTime now) {
        appointment.cancel(now); // flushed automatically right before the slot update below
        slots.markAvailable(appointment.getSlot().getId());
    }

    private void enforceCancellationWindow(Appointment appointment, LocalDateTime now) {
        if (now.plusHours(cancellationWindowHours).isAfter(appointment.getSlot().getStartTime())) {
            throw new BusinessRuleException("Appointments can only be cancelled or rescheduled at least "
                    + cancellationWindowHours + " hours before they start");
        }
    }

    private Appointment findOwned(Long patientId, Long appointmentId) {
        Appointment appointment = appointments.findDetailedById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " not found"));
        if (!appointment.getPatient().getId().equals(patientId)) {
            // 404 rather than 403, so one patient cannot probe which appointment ids exist
            throw new NotFoundException("Appointment " + appointmentId + " not found");
        }
        return appointment;
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessRuleException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return trimmed;
    }
}
