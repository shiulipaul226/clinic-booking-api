package com.clinic.booking.service;

import com.clinic.booking.domain.Role;
import com.clinic.booking.domain.User;
import com.clinic.booking.dto.AuthResponse;
import com.clinic.booking.dto.LoginRequest;
import com.clinic.booking.dto.RegisterRequest;
import com.clinic.booking.exception.ConflictException;
import com.clinic.booking.repository.UserRepository;
import com.clinic.booking.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /** Self-registration always creates a PATIENT. Admins are created by the seeder only. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = users.save(new User(
                request.name().trim(), email, encoder.encode(request.password()), Role.PATIENT,
                LocalDateTime.now(clock)));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Same message for "no such user" and "wrong password" so the API does not leak which emails exist.
        User user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generate(user), "Bearer", jwtService.getExpirationSeconds(),
                user.getId(), user.getName(), user.getRole());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
