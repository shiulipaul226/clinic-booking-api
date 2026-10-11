package com.clinic.booking.controller;

import com.clinic.booking.config.OpenApiConfig;
import com.clinic.booking.dto.DoctorRequest;
import com.clinic.booking.dto.DoctorResponse;
import com.clinic.booking.dto.GenerateSlotsRequest;
import com.clinic.booking.dto.SlotResponse;
import com.clinic.booking.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@Tag(name = "Doctors & slots")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @Operation(summary = "List all doctors (public)")
    @GetMapping
    public List<DoctorResponse> list() {
        return doctorService.list();
    }

    @Operation(summary = "List a doctor's slots for one date (public)",
            description = "Use onlyAvailable=true to hide slots that are already booked.")
    @GetMapping("/{doctorId}/slots")
    public List<SlotResponse> slots(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean onlyAvailable) {
        return doctorService.listSlots(doctorId, date, onlyAvailable);
    }

    @Operation(summary = "Add a doctor (admin)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorResponse create(@Valid @RequestBody DoctorRequest request) {
        return doctorService.create(request);
    }

    @Operation(summary = "Generate slots for a doctor on one date (admin)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @PostMapping("/{doctorId}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SlotResponse> generateSlots(@PathVariable Long doctorId,
                                            @Valid @RequestBody GenerateSlotsRequest request) {
        return doctorService.generateSlots(doctorId, request);
    }
}
