package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.CreateSessionRequest;
import com.example.learnerassignments.dto.SessionResponse;
import com.example.learnerassignments.dto.SessionSubmissionOverviewResponse;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.security.CurrentStaff;
import com.example.learnerassignments.security.StaffPrincipal;
import com.example.learnerassignments.service.FeedbackReleaseService;
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
    private final FeedbackReleaseService feedbackReleaseService;

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

    /**
     * Releases this session's marking to its learners.
     *
     * Scoped like reading the session is: somebody who cannot see a session cannot publish its
     * results, and an assessor or moderator holding no assignment rows reaches nothing. Not
     * found rather than forbidden, as everywhere else.
     *
     * Releasing twice is not an error. It publishes whatever is newly marked and says so; the
     * facilitator who marks the last three scripts after releasing should not have to think
     * about whether pressing it again will re-notify the whole cohort. It will not — only
     * learners whose work was published by that call are told.
     */
    @PostMapping("/{id}/release-feedback")
    public ResponseEntity<?> releaseFeedback(@PathVariable Long id, Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        if (!scopeService.canAccessSession(principal, id)) {
            throw new ResourceNotFoundException("Submission session not found with id: " + id);
        }

        FeedbackReleaseService.ReleaseResult result = feedbackReleaseService.release(id);

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("published", result.published());
        body.put("alreadyPublished", result.alreadyPublished());
        body.put("notified", result.notified());
        body.put("unmarked", result.unmarked());
        body.put("message", result.unmarked() > 0
                ? result.published() + " learner(s) can now see their marking. "
                        + result.unmarked() + " submission(s) in this session have not been marked yet "
                        + "and were left alone."
                : result.published() + " learner(s) can now see their marking.");
        return ResponseEntity.ok(body);
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
