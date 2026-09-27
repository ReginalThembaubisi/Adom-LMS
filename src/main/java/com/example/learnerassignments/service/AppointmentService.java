package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.ApplicationDtos.AppointmentDto;
import com.example.learnerassignments.dto.ApplicationDtos.AppointmentSubmitRequest;
import com.example.learnerassignments.dto.ApplicationDtos.AppointmentUpdateRequest;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.AppointmentRequest;
import com.example.learnerassignments.model.AppointmentStatus;
import com.example.learnerassignments.repository.AppointmentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Appointment requests from the website's Services page, and staff follow-up on them. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRequestRepository repository;

    @Transactional
    public void submit(AppointmentSubmitRequest request, String sourceIp) {
        if (StringUtils.hasText(request.getWebsite())) {
            log.warn("Dropped an appointment request from {} that filled in the honeypot field.", sourceIp);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AppointmentRequest saved = repository.save(AppointmentRequest.builder()
                .fullName(request.getFullName().trim())
                .company(trim(request.getCompany()))
                .email(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .phone(request.getPhone().trim())
                .service(request.getService().trim())
                .preferredDate(request.getPreferredDate())
                .preferredTime(trim(request.getPreferredTime()))
                .message(trim(request.getMessage()))
                .status(AppointmentStatus.NEW)
                .sourceIp(sourceIp)
                .createdAt(now)
                .updatedAt(now)
                .build());
        log.info("Appointment request {} received for {}.", saved.getId(), saved.getService());
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> list(String status) {
        AppointmentStatus filter = StringUtils.hasText(status) ? parseStatus(status) : null;
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> filter == null || a.getStatus() == filter)
                .map(AppointmentService::toDto)
                .toList();
    }

    @Transactional
    public AppointmentDto update(Long id, AppointmentUpdateRequest request) {
        AppointmentRequest appointment = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found: " + id));
        if (StringUtils.hasText(request.getStatus())) {
            appointment.setStatus(parseStatus(request.getStatus()));
        }
        if (request.getStaffNotes() != null) {
            appointment.setStaffNotes(trim(request.getStaffNotes()));
        }
        appointment.setUpdatedAt(LocalDateTime.now());
        return toDto(appointment);
    }

    private static AppointmentStatus parseStatus(String raw) {
        try {
            return AppointmentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status must be NEW, CONFIRMED, COMPLETED or CANCELLED.");
        }
    }

    private static AppointmentDto toDto(AppointmentRequest a) {
        return AppointmentDto.builder()
                .id(a.getId())
                .fullName(a.getFullName())
                .company(a.getCompany())
                .email(a.getEmail())
                .phone(a.getPhone())
                .service(a.getService())
                .preferredDate(a.getPreferredDate())
                .preferredTime(a.getPreferredTime())
                .message(a.getMessage())
                .status(a.getStatus().name())
                .staffNotes(a.getStaffNotes())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
