package com.clinic.booking.security;

import com.clinic.booking.domain.Role;

/** The authenticated caller, extracted from the JWT and available via @AuthenticationPrincipal. */
public record AuthUser(Long id, String email, Role role) {
}
