package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.OpeningDtos.OpeningDto;
import com.example.learnerassignments.dto.OpeningDtos.OpeningRequest;
import com.example.learnerassignments.service.AuditLogService;
import com.example.learnerassignments.service.OpeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Job and internship openings for the Careers page. Admin-only through /api/admin/**. */
@RestController
@RequestMapping("/api/admin/openings")
@RequiredArgsConstructor
public class AdminOpeningController {

    private final OpeningService openingService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<OpeningDto> list() {
        return openingService.listForAdmin();
    }

    @PostMapping
    public ResponseEntity<OpeningDto> create(@Valid @RequestBody OpeningRequest request, Authentication auth) {
        OpeningDto dto = openingService.create(request);
        auditLogService.log(auth, "CREATE_OPENING", "JobOpening", dto.getId(), dto.getTitle());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public OpeningDto update(@PathVariable Long id, @Valid @RequestBody OpeningRequest request, Authentication auth) {
        OpeningDto dto = openingService.update(id, request);
        auditLogService.log(auth, "UPDATE_OPENING", "JobOpening", id, dto.getTitle() + " [" + dto.getStatus() + "]");
        return dto;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        openingService.delete(id);
        auditLogService.log(auth, "DELETE_OPENING", "JobOpening", id, null);
        return ResponseEntity.noContent().build();
    }
}
