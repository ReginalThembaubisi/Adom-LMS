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
    public ResponseEntity<LearnerAuthResponse> registerLearner(@Valid @RequestBody CreateLearnerRequest request) {
        LearnerAuthResponse response = learnerService.registerLearner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LearnerAuthResponse> loginStudent(@Valid @RequestBody StudentLoginRequest request) {
        LearnerAuthResponse response = learnerService.loginStudent(request);
        return ResponseEntity.ok(response);
    }

    // The full roster. Staff only — it used to be open, and learner codes are handed out over
    // email and WhatsApp, so an open roster was a list of usable identifiers.
    @GetMapping
    public ResponseEntity<List<LearnerSummaryResponse>> getAllLearners() {
        List<LearnerSummaryResponse> learners = learnerService.getAllLearners();
        return ResponseEntity.ok(learners);
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
