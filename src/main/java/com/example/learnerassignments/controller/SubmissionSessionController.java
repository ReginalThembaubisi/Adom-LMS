package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.CreateSessionRequest;
import com.example.learnerassignments.dto.SessionResponse;
import com.example.learnerassignments.dto.SessionSubmissionOverviewResponse;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.security.CurrentStaff;
import com.example.learnerassignments.security.StaffPrincipal;
import com.example.learnerassignments.service.ScopeService;
import com.example.learnerassignments.service.SubmissionService;
import com.example.learnerassignments.service.SubmissionSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SubmissionSessionController {

    private final SubmissionSessionService sessionService;
    private final SubmissionService submissionService;
    private final CurrentStaff currentStaff;
    private final ScopeService scopeService;

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        SessionResponse response = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getAllSessions(Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        java.util.Set<Long> visible = scopeService.accessibleSessions(principal).stream()
                .map(com.example.learnerassignments.model.SubmissionSession::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<SessionResponse> sessions = sessionService.getAllSessions().stream()
                .filter(s -> visible.contains(s.getId()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/active")
    public ResponseEntity<List<SessionResponse>> getActiveSessions(Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        java.util.Set<Long> visible = scopeService.accessibleSessions(principal).stream()
                .map(com.example.learnerassignments.model.SubmissionSession::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<SessionResponse> activeSessions = sessionService.getActiveSessions().stream()
                .filter(s -> visible.contains(s.getId()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(activeSessions);
    }

    @GetMapping("/{id}/submissions")
    public ResponseEntity<SessionSubmissionOverviewResponse> getSessionSubmissions(
            @PathVariable Long id, Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        if (!scopeService.canAccessSession(principal, id)) {
            throw new ResourceNotFoundException("Submission session not found with id: " + id);
        }
        // Reaching a session is not the same as seeing everyone in it.
        SessionSubmissionOverviewResponse overview = submissionService.getSessionSubmissionsOverview(
                id, principal.isAdmin() ? null : scopeService.accessibleLearnerIds(principal));
        return ResponseEntity.ok(overview);
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<SessionResponse> closeSession(@PathVariable Long id) {
        SessionResponse response = sessionService.manuallyCloseSession(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/open")
    public ResponseEntity<SessionResponse> openSession(@PathVariable Long id) {
        SessionResponse response = sessionService.manuallyOpenSession(id);
        return ResponseEntity.ok(response);
    }
}
