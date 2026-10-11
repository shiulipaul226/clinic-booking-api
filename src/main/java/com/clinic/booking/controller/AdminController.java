package com.clinic.booking.controller;

import com.clinic.booking.config.OpenApiConfig;
import com.clinic.booking.dto.AppointmentResponse;
import com.clinic.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminController {

    private final BookingService bookingService;

    public AdminController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "List every appointment in the clinic")
    @GetMapping("/appointments")
    public List<AppointmentResponse> allAppointments() {
        return bookingService.allAppointments();
    }
}
