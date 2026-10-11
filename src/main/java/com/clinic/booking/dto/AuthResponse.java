package com.clinic.booking.dto;

import com.clinic.booking.domain.Role;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, Long userId, String name, Role role) {
}
