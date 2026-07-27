package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.service.LearnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learners")
@RequiredArgsConstructor
public class LearnerController {

    private final LearnerService learnerService;

    @PostMapping
    public ResponseEntity<LearnerResponse> registerLearner(@Valid @RequestBody CreateLearnerRequest request) {
        LearnerResponse response = learnerService.registerLearner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LearnerResponse> loginStudent(@Valid @RequestBody StudentLoginRequest request) {
        LearnerResponse response = learnerService.loginStudent(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{studentNumber}/submissions")
    public ResponseEntity<List<StudentSubmissionHistoryDto>> getLearnerSubmissions(@PathVariable String studentNumber) {
        List<StudentSubmissionHistoryDto> submissions = learnerService.getLearnerSubmissions(studentNumber);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping
    public ResponseEntity<List<LearnerSummaryResponse>> getAllLearners() {
        List<LearnerSummaryResponse> learners = learnerService.getAllLearners();
        return ResponseEntity.ok(learners);
    }

    @GetMapping("/{code}")
    public ResponseEntity<LearnerResponse> getLearnerByCode(@PathVariable String code) {
        LearnerResponse learner = learnerService.getLearnerByCode(code);
        return ResponseEntity.ok(learner);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        learnerService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        learnerService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
