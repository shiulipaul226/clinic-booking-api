package com.clinic.booking.controller;

import com.clinic.booking.config.OpenApiConfig;
import com.clinic.booking.dto.AppointmentResponse;
import com.clinic.booking.dto.BookingRequest;
import com.clinic.booking.dto.BookingResult;
import com.clinic.booking.dto.RescheduleRequest;
import com.clinic.booking.security.AuthUser;
import com.clinic.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments (patient)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AppointmentController {

    private final BookingService bookingService;

    public AppointmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Book a slot",
            description = "Send an optional Idempotency-Key header (any unique string). Retrying with the same key "
                    + "returns the original appointment with 200 and an Idempotent-Replayed header instead of "
                    + "booking twice. Returns 409 if the slot was taken by someone else.")
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(
            @AuthenticationPrincipal AuthUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingRequest request) {
        BookingResult result = bookingService.book(user.id(), request.slotId(), idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.appointment());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.appointment());
    }

    @Operation(summary = "List my appointments")
    @GetMapping("/me")
    public List<AppointmentResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return bookingService.myAppointments(user.id());
    }

    @Operation(summary = "Cancel one of my appointments",
            description = "Allowed until 2 hours before the appointment starts (configurable).")
    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookingService.cancel(user.id(), id);
    }

    @Operation(summary = "Move one of my appointments to another slot of the same doctor",
            description = "Atomic: if the new slot is taken you get 409 and keep your original appointment.")
    @PostMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @Valid @RequestBody RescheduleRequest request) {
        return bookingService.reschedule(user.id(), id, request.newSlotId());
    }
}
