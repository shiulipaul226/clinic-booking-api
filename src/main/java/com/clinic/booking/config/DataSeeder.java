package com.clinic.booking.config;

import com.clinic.booking.domain.Role;
import com.clinic.booking.domain.User;
import com.clinic.booking.dto.DoctorRequest;
import com.clinic.booking.dto.DoctorResponse;
import com.clinic.booking.dto.GenerateSlotsRequest;
import com.clinic.booking.repository.DoctorRepository;
import com.clinic.booking.repository.UserRepository;
import com.clinic.booking.service.DoctorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** On startup: makes sure an admin exists and (optionally) loads demo doctors and slots so you can try the API. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final DoctorRepository doctors;
    private final DoctorService doctorService;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean seedDemoData;

    public DataSeeder(UserRepository users,
                      DoctorRepository doctors,
                      DoctorService doctorService,
                      PasswordEncoder encoder,
                      Clock clock,
                      @Value("${app.admin.email}") String adminEmail,
                      @Value("${app.admin.password}") String adminPassword,
                      @Value("${app.seed-demo-data}") boolean seedDemoData) {
        this.users = users;
        this.doctors = doctors;
        this.doctorService = doctorService;
        this.encoder = encoder;
        this.clock = clock;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
        this.seedDemoData = seedDemoData;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmail(adminEmail)) {
            users.save(new User("Clinic Admin", adminEmail, encoder.encode(adminPassword), Role.ADMIN,
                    LocalDateTime.now(clock)));
            log.info("Created admin user {}", adminEmail);
        }

        if (seedDemoData && doctors.count() == 0) {
            DoctorResponse cardiologist = doctorService.create(new DoctorRequest("Dr. Anita Sharma", "Cardiology"));
            DoctorResponse dermatologist = doctorService.create(new DoctorRequest("Dr. Rahul Verma", "Dermatology"));
            LocalDate today = LocalDate.now(clock);
            for (int day = 1; day <= 3; day++) {
                LocalDate date = today.plusDays(day);
                var request = new GenerateSlotsRequest(date, LocalTime.of(9, 0), LocalTime.of(13, 0), 30);
                doctorService.generateSlots(cardiologist.id(), request);
                doctorService.generateSlots(dermatologist.id(), request);
            }
            log.info("Seeded 2 demo doctors with slots for the next 3 days");
        }
    }
}
