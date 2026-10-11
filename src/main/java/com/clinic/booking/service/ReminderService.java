package com.clinic.booking.service;

import com.clinic.booking.domain.Appointment;
import com.clinic.booking.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final AppointmentRepository appointments;
    private final Clock clock;
    private final int leadHours;

    public ReminderService(AppointmentRepository appointments, Clock clock,
                           @Value("${clinic.reminder.lead-hours}") int leadHours) {
        this.appointments = appointments;
        this.clock = clock;
        this.leadHours = leadHours;
    }

    /**
     * Finds booked appointments starting within the next {@code leadHours} that have not been reminded yet,
     * "sends" the reminder (logged here; plug in an email/SMS provider in a real deployment) and flags them
     * so they are never reminded twice.
     *
     * @return how many reminders were sent
     */
    @Transactional
    public int sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> due = appointments.findDueForReminder(now, now.plusHours(leadHours));
        for (Appointment a : due) {
            log.info("REMINDER -> {} <{}>: appointment #{} with {} at {}",
                    a.getPatient().getName(), a.getPatient().getEmail(), a.getId(),
                    a.getSlot().getDoctor().getName(), a.getSlot().getStartTime());
            a.markReminderSent();
        }
        return due.size();
    }
}
