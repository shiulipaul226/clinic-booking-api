package com.clinic.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /**
     * One clock for the whole app, in the clinic's own timezone. Slot times are stored as local clinic
     * time, and injecting the Clock (instead of calling LocalDateTime.now()) keeps time-based rules testable.
     */
    @Bean
    public Clock clock(@Value("${clinic.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
