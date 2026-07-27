package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.CreateSessionRequest;
import com.example.learnerassignments.dto.SessionResponse;
import com.example.learnerassignments.dto.SessionSubmissionOverviewResponse;
import com.example.learnerassignments.service.SubmissionService;
import com.example.learnerassignments.service.SubmissionSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SubmissionSessionController {

    private final SubmissionSessionService sessionService;
    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        SessionResponse response = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getAllSessions() {
        List<SessionResponse> sessions = sessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/active")
    public ResponseEntity<List<SessionResponse>> getActiveSessions() {
        List<SessionResponse> activeSessions = sessionService.getActiveSessions();
        return ResponseEntity.ok(activeSessions);
    }

    @GetMapping("/{id}/submissions")
    public ResponseEntity<SessionSubmissionOverviewResponse> getSessionSubmissions(@PathVariable Long id) {
        SessionSubmissionOverviewResponse overview = submissionService.getSessionSubmissionsOverview(id);
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
