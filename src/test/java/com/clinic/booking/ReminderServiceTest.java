package com.clinic.booking;

import com.clinic.booking.domain.Appointment;
import com.clinic.booking.domain.Doctor;
import com.clinic.booking.domain.Role;
import com.clinic.booking.domain.Slot;
import com.clinic.booking.domain.User;
import com.clinic.booking.repository.AppointmentRepository;
import com.clinic.booking.repository.DoctorRepository;
import com.clinic.booking.repository.SlotRepository;
import com.clinic.booking.repository.UserRepository;
import com.clinic.booking.service.BookingService;
import com.clinic.booking.service.ReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderServiceTest extends AbstractIntegrationTest {

    @Autowired ReminderService reminderService;
    @Autowired BookingService bookingService;
    @Autowired UserRepository users;
    @Autowired DoctorRepository doctors;
    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired Clock clock;

    @Test
    void remindsOnlyAppointmentsStartingWithin24Hours_andOnlyOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        Doctor doctor = doctors.save(new Doctor("Dr. Reminder", "General"));
        User patient = users.save(new User("Remy", "remy-" + UUID.randomUUID() + "@test.com", "x",
                Role.PATIENT, now));

        LocalDateTime soonStart = now.plusHours(5).withNano(0);
        LocalDateTime laterStart = now.plusDays(3).withNano(0);
        Slot soon = slots.save(new Slot(doctor, soonStart, soonStart.plusMinutes(30)));
        Slot later = slots.save(new Slot(doctor, laterStart, laterStart.plusMinutes(30)));

        Long soonId = bookingService.book(patient.getId(), soon.getId(), null).appointment().id();
        Long laterId = bookingService.book(patient.getId(), later.getId(), null).appointment().id();

        assertThat(reminderService.sendDueReminders()).isGreaterThanOrEqualTo(1);

        Appointment soonAppointment = appointments.findById(soonId).orElseThrow();
        Appointment laterAppointment = appointments.findById(laterId).orElseThrow();
        assertThat(soonAppointment.isReminderSent()).as("5h away -> reminded").isTrue();
        assertThat(laterAppointment.isReminderSent()).as("3 days away -> not yet").isFalse();

        // a second run must not pick the same appointment up again
        LocalDateTime check = LocalDateTime.now(clock);
        assertThat(appointments.findDueForReminder(check, check.plusHours(24)))
                .extracting(Appointment::getId)
                .doesNotContain(soonId);
    }
}
