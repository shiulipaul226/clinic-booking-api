package com.clinic.booking.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless token API, no browser session/cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/actuator/health").permitAll()
                        // anyone can browse doctors and their slots
                        .requestMatchers(HttpMethod.GET, "/api/doctors", "/api/doctors/**").permitAll()
                        // everything else under /api/doctors (create doctor, generate slots) and /api/admin is admin-only
                        .requestMatchers("/api/doctors/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/appointments/**").hasRole("PATIENT")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeProblem(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                        "Missing or invalid token"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeProblem(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                        "You do not have permission to perform this action")))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * We authenticate with JWTs, not Spring's built-in user store. Declaring this bean stops Spring Boot
     * from creating a default in-memory user with a random password on startup.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Password login is handled by AuthService");
        };
    }

    private static void writeProblem(HttpServletResponse res, int status, String title, String detail)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.getWriter().write("{\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
