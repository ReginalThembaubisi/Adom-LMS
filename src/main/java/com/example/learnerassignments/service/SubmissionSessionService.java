package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.CreateSessionRequest;
import com.example.learnerassignments.dto.SessionResponse;
import com.example.learnerassignments.exception.InvalidFileException;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Assignment;
import com.example.learnerassignments.model.SessionStatus;
import com.example.learnerassignments.model.SubmissionSession;
import com.example.learnerassignments.repository.AssignmentRepository;
import com.example.learnerassignments.repository.SubmissionSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionSessionService {

    private final SubmissionSessionRepository sessionRepository;
    private final AssignmentRepository assignmentRepository;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + request.getAssignmentId()));

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new InvalidFileException("End time cannot be before start time.");
        }

        LocalDateTime now = LocalDateTime.now();
        SessionStatus initialStatus;

        if (request.getEndTime().isBefore(now)) {
            initialStatus = SessionStatus.CLOSED;
        } else if (request.getStartTime().isAfter(now)) {
            initialStatus = SessionStatus.SCHEDULED;
        } else {
            initialStatus = SessionStatus.OPEN;
        }

        SubmissionSession session = SubmissionSession.builder()
                .sessionName(request.getSessionName())
                .assignment(assignment)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(initialStatus)
                .build();

        SubmissionSession saved = sessionRepository.save(session);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getAllSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions() {
        return sessionRepository.findActiveSessions(LocalDateTime.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SessionResponse manuallyCloseSession(Long id) {
        SubmissionSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));

        session.setStatus(SessionStatus.CLOSED);
        SubmissionSession updated = sessionRepository.save(session);
        return mapToResponse(updated);
    }

    @Transactional
    public SessionResponse manuallyOpenSession(Long id) {
        SubmissionSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));

        LocalDateTime now = LocalDateTime.now();
        if (session.getEndTime() == null || session.getEndTime().isBefore(now)) {
            LocalDateTime newEndTime = now.plusDays(1);
            session.setEndTime(newEndTime);
            if (session.getAssignment() != null) {
                session.getAssignment().setDueDate(newEndTime);
            }
        }
        if (session.getStartTime() == null || session.getStartTime().isAfter(now)) {
            session.setStartTime(now);
        }

        session.setStatus(SessionStatus.OPEN);
        SubmissionSession updated = sessionRepository.save(session);
        return mapToResponse(updated);
    }

    @Transactional
    public void updateSessionStatusesCron() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Flip SCHEDULED to OPEN once start_time has passed (and end_time is in future)
        List<SubmissionSession> toOpen = sessionRepository
                .findByStatusAndStartTimeLessThanEqualAndEndTimeGreaterThan(SessionStatus.SCHEDULED, now, now);
        for (SubmissionSession session : toOpen) {
            session.setStatus(SessionStatus.OPEN);
            sessionRepository.save(session);
        }

        // 2. Flip OPEN/SCHEDULED to CLOSED once end_time has passed
        List<SubmissionSession> toClose = sessionRepository.findExpiredNonClosedSessions(now);
        for (SubmissionSession session : toClose) {
            session.setStatus(SessionStatus.CLOSED);
            sessionRepository.save(session);
        }
    }

    private SessionResponse mapToResponse(SubmissionSession session) {
        return SessionResponse.builder()
                .id(session.getId())
                .sessionName(session.getSessionName())
                .assignmentId(session.getAssignment().getId())
                .assignmentTitle(session.getAssignment().getTitle())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
