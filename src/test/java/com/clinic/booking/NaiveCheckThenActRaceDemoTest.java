package com.clinic.booking;

import com.clinic.booking.domain.Appointment;
import com.clinic.booking.domain.AppointmentStatus;
import com.clinic.booking.domain.Doctor;
import com.clinic.booking.domain.Role;
import com.clinic.booking.domain.Slot;
import com.clinic.booking.domain.User;
import com.clinic.booking.repository.AppointmentRepository;
import com.clinic.booking.repository.DoctorRepository;
import com.clinic.booking.repository.SlotRepository;
import com.clinic.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE "BEFORE" PICTURE (a demonstration, not a feature test).
 *
 * <p>This reproduces the classic bug that BookingService avoids: "check if the slot is free, then insert".
 * Between the check and the insert there is a gap (here 10 ms, standing in for real-world latency such as a
 * payment call or a slow query), so many requests see "free" and all of them book the same slot.
 *
 * <p>It lives in the test folder on purpose: it documents the race condition you reproduced and then fixed,
 * which is exactly the story for the "Situation" part of your STAR write-up.
 */
class NaiveCheckThenActRaceDemoTest extends AbstractIntegrationTest {

    private static final int PATIENTS = 50;

    @Autowired PlatformTransactionManager txManager;
    @Autowired UserRepository users;
    @Autowired DoctorRepository doctors;
    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired Clock clock;

    @Test
    void checkThenInsertWithoutAnAtomicClaim_doubleBooksTheSameSlot() throws Exception {
        Doctor doctor = doctors.save(new Doctor("Dr. Naive", "Race conditions"));
        LocalDateTime start = LocalDateTime.now(clock).plusDays(3).withNano(0);
        Slot slot = slots.save(new Slot(doctor, start, start.plusMinutes(30)));
        Long slotId = slot.getId();

        List<Long> patientIds = new ArrayList<>();
        for (int i = 0; i < PATIENTS; i++) {
            patientIds.add(users.save(new User("Naive " + i, "naive-" + UUID.randomUUID() + "@test.com", "x",
                    Role.PATIENT, LocalDateTime.now(clock))).getId());
        }

        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(PATIENTS);
        CountDownLatch ready = new CountDownLatch(PATIENTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (Long patientId : patientIds) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    tx.executeWithoutResult(status -> {
                        // 1) CHECK: is anybody booked on this slot yet?
                        boolean free = appointments.countBySlotIdAndStatus(slotId, AppointmentStatus.BOOKED) == 0;
                        if (free) {
                            pause(10);                           // 2) the gap in which everybody else also sees "free"
                            // 3) ACT: insert the booking
                            appointments.save(new Appointment(slots.getReferenceById(slotId),
                                    users.getReferenceById(patientId), null, LocalDateTime.now(clock)));
                        }
                    });
                } catch (Throwable t) {
                    errors.add(t);
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        long booked = appointments.countBySlotIdAndStatus(slotId, AppointmentStatus.BOOKED);
        System.out.printf("[naive race demo] check-then-insert produced %d bookings for ONE slot "
                + "(the real BookingService produces exactly 1)%n", booked);

        assertThat(errors).isEmpty();
        assertThat(booked).as("double bookings caused by the race").isGreaterThan(1);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
