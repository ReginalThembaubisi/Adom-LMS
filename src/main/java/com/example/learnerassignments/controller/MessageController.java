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

    private Learner requireLearner(String code) {
        return learnerRepository.findByLearnerCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Student number not found: " + code));
    }

    private Lecturer requireLecturer(String username) {
        return lecturerRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer record not found"));
    }

    // --- Student side ---

    @GetMapping("/api/learners/{code}/facilitators")
    public ResponseEntity<List<PersonSummaryDto>> getFacilitators(@PathVariable String code) {
        Learner learner = requireLearner(code);
        return ResponseEntity.ok(messageService.getFacilitatorsForLearner(learner));
    }

    @GetMapping("/api/learners/{code}/messages")
    public ResponseEntity<List<MessageThreadSummaryDto>> getLearnerThreads(@PathVariable String code) {
        Learner learner = requireLearner(code);
        return ResponseEntity.ok(messageService.getThreadSummariesForLearner(learner));
    }

    @GetMapping("/api/learners/{code}/messages/unread-count")
    public ResponseEntity<Map<String, Long>> getLearnerUnreadCount(@PathVariable String code) {
        Learner learner = requireLearner(code);
        return ResponseEntity.ok(Map.of("unreadCount", messageService.getUnreadCountForLearner(learner)));
    }

    @GetMapping("/api/learners/{code}/messages/{lecturerId}")
    public ResponseEntity<?> getLearnerThread(@PathVariable String code, @PathVariable Long lecturerId) {
        Learner learner = requireLearner(code);
        if (!messageService.facilitatorOwnsLearner(lecturerId, learner)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("That facilitator does not teach any of your modules.");
        }
        return ResponseEntity.ok(messageService.getThreadAndMarkRead(learner.getId(), lecturerId, SenderType.LEARNER));
    }

    @PostMapping("/api/learners/{code}/messages/{lecturerId}")
    public ResponseEntity<?> sendFromLearner(@PathVariable String code, @PathVariable Long lecturerId,
                                              @Valid @RequestBody SendMessageRequest request) {
        Learner learner = requireLearner(code);
        if (!messageService.facilitatorOwnsLearner(lecturerId, learner)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("That facilitator does not teach any of your modules.");
        }
        Lecturer lecturer = lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Facilitator not found"));
        MessageDto saved = messageService.sendMessage(learner, lecturer, SenderType.LEARNER, request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

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
