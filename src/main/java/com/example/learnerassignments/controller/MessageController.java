package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.MessageDto;
import com.example.learnerassignments.dto.MessageThreadSummaryDto;
import com.example.learnerassignments.dto.PersonSummaryDto;
import com.example.learnerassignments.dto.SendMessageRequest;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.model.SenderType;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final LearnerRepository learnerRepository;
    private final LecturerRepository lecturerRepository;
    private final MessageService messageService;

    private Lecturer requireLecturer(String username) {
        return lecturerRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer record not found"));
    }

    // The learner side of messaging now lives on /api/me/messages, where the learner is
    // resolved from the session. These routes took the learner code from the path and were
    // open, so anyone could read — and send as — any learner in the cohort.

    // --- Facilitator side ---

    @GetMapping("/api/lecturer/messages")
    public ResponseEntity<List<MessageThreadSummaryDto>> getLecturerThreads(Authentication auth) {
        Lecturer lecturer = requireLecturer(auth.getName());
        return ResponseEntity.ok(messageService.getThreadSummariesForLecturer(lecturer.getId()));
    }

    @GetMapping("/api/lecturer/messages/unread-count")
    public ResponseEntity<Map<String, Long>> getLecturerUnreadCount(Authentication auth) {
        Lecturer lecturer = requireLecturer(auth.getName());
        return ResponseEntity.ok(Map.of("unreadCount", messageService.getUnreadCountForLecturer(lecturer.getId())));
    }

    // e.g. "Online class starting now" — sends to every student enrolled in any of the
    // lecturer's modules, each landing in that student's normal 1:1 thread with the lecturer.
    @PostMapping("/api/lecturer/messages/broadcast")
    public ResponseEntity<Map<String, Integer>> broadcastFromLecturer(@Valid @RequestBody SendMessageRequest request, Authentication auth) {
        Lecturer lecturer = requireLecturer(auth.getName());
        int sentCount = messageService.sendBroadcast(lecturer, request.getBody());
        return ResponseEntity.ok(Map.of("sentCount", sentCount));
    }

    @GetMapping("/api/lecturer/messages/{learnerId}")
    public ResponseEntity<?> getLecturerThread(@PathVariable Long learnerId, Authentication auth) {
        Lecturer lecturer = requireLecturer(auth.getName());
        Learner learner = learnerRepository.findById(learnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (!messageService.facilitatorOwnsLearner(lecturer.getId(), learner)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("This student is not enrolled in any of your modules.");
        }
        return ResponseEntity.ok(messageService.getThreadAndMarkRead(learnerId, lecturer.getId(), SenderType.LECTURER));
    }

    @PostMapping("/api/lecturer/messages/{learnerId}")
    public ResponseEntity<?> sendFromLecturer(@PathVariable Long learnerId, @Valid @RequestBody SendMessageRequest request,
                                               Authentication auth) {
        Lecturer lecturer = requireLecturer(auth.getName());
        Learner learner = learnerRepository.findById(learnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (!messageService.facilitatorOwnsLearner(lecturer.getId(), learner)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("This student is not enrolled in any of your modules.");
        }
        MessageDto saved = messageService.sendMessage(learner, lecturer, SenderType.LECTURER, request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
