package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.ApplicationDtos.AppointmentDto;
import com.example.learnerassignments.dto.ApplicationDtos.AppointmentUpdateRequest;
import com.example.learnerassignments.service.AppointmentService;
import com.example.learnerassignments.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Appointment requests from the Services page. Admin-only through /api/admin/**. */
@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentService appointmentService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<AppointmentDto> list(@RequestParam(required = false) String status) {
        return appointmentService.list(status);
    }

    @PutMapping("/{id}")
    public AppointmentDto update(@PathVariable Long id, @Valid @RequestBody AppointmentUpdateRequest request,
                                 Authentication auth) {
        AppointmentDto dto = appointmentService.update(id, request);
        auditLogService.log(auth, "UPDATE_APPOINTMENT", "AppointmentRequest", id, dto.getStatus());
        return dto;
    }
}
