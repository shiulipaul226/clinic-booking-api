package com.clinic.booking;

import com.clinic.booking.domain.AppointmentStatus;
import com.clinic.booking.domain.Doctor;
import com.clinic.booking.domain.Role;
import com.clinic.booking.domain.Slot;
import com.clinic.booking.domain.SlotStatus;
import com.clinic.booking.domain.User;
import com.clinic.booking.exception.ConflictException;
import com.clinic.booking.repository.AppointmentRepository;
import com.clinic.booking.repository.DoctorRepository;
import com.clinic.booking.repository.SlotRepository;
import com.clinic.booking.repository.UserRepository;
import com.clinic.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE test of this project: many different patients try to book the same slot at the same instant.
 * Exactly one must win, everyone else must get a clean ConflictException (HTTP 409), nothing else may go wrong.
 */
class BookingConcurrencyTest extends AbstractIntegrationTest {

    private static final int PATIENTS = 50;

    @Autowired BookingService bookingService;
    @Autowired UserRepository users;
    @Autowired DoctorRepository doctors;
    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired Clock clock;

    @Test
    void fiftyPatientsBookingTheSameSlotAtOnce_exactlyOneWins() throws Exception {
        Doctor doctor = doctors.save(new Doctor("Dr. Race", "Concurrency"));
        LocalDateTime start = LocalDateTime.now(clock).plusDays(2).withNano(0);
        Slot slot = slots.save(new Slot(doctor, start, start.plusMinutes(30)));

        List<Long> patientIds = new ArrayList<>();
        for (int i = 0; i < PATIENTS; i++) {
            User u = users.save(new User("Patient " + i, "race-" + UUID.randomUUID() + "@test.com", "x",
                    Role.PATIENT, LocalDateTime.now(clock)));
            patientIds.add(u.getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(PATIENTS);
        CountDownLatch ready = new CountDownLatch(PATIENTS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (Long patientId : patientIds) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();                                   // all threads released at the same moment
                    bookingService.book(patientId, slot.getId(), null);
                    successes.incrementAndGet();
                } catch (ConflictException e) {
                    conflicts.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unexpected).as("no unexpected errors").isEmpty();
        assertThat(successes.get()).as("successful bookings").isEqualTo(1);
        assertThat(conflicts.get()).as("clean 409 conflicts").isEqualTo(PATIENTS - 1);
        assertThat(appointments.countBySlotIdAndStatus(slot.getId(), AppointmentStatus.BOOKED))
                .as("BOOKED appointments stored for the slot").isEqualTo(1);
        assertThat(slots.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);

        System.out.printf("[concurrency] %d simultaneous requests -> %d booked, %d conflicts%n",
                PATIENTS, successes.get(), conflicts.get());
    }
}
