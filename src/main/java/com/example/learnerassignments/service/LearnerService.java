package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LearnerCodeSequence;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.LearnerCodeSequenceRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearnerService {

    private final LearnerRepository learnerRepository;
    private final LearnerCodeSequenceRepository sequenceRepository;
    private final SubmissionRepository submissionRepository;
    private final ModuleRepository moduleRepository;
    private final com.example.learnerassignments.repository.LearnershipRepository learnershipRepository;
    private final EmailService emailService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.example.learnerassignments.repository.SystemSettingRepository systemSettingRepository;

    @Transactional
    public LearnerResponse registerLearner(CreateLearnerRequest request) {
        boolean regOpen = systemSettingRepository.findById("REGISTRATION_OPEN")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);
        if (!regOpen) {
            throw new IllegalArgumentException("Student registration is currently closed by the administrator.");
        }

        String generatedCode = generateNextLearnerCode();

        List<Module> learnershipModules = java.util.Collections.emptyList();
        com.example.learnerassignments.model.Learnership learnership = null;
        if (request.getLearnershipId() != null) {
            learnership = learnershipRepository.findById(request.getLearnershipId()).orElse(null);
            if (learnership != null) {
                learnershipModules = moduleRepository.findByCategoryLearnershipId(request.getLearnershipId());
            }
        }

        Learner learner = Learner.builder()
                .learnerCode(generatedCode)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .idNumber(request.getIdNumber())
                .phoneNumber(request.getPhoneNumber())
                .cohort(request.getCohort())
                .learnership(learnership)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .modules(new HashSet<>(learnershipModules))
                .build();

        Learner savedLearner = learnerRepository.save(learner);

        // Asynchronously dispatch confirmation email (non-blocking)
        if (savedLearner.getEmail() != null && !savedLearner.getEmail().isBlank()) {
            emailService.sendRegistrationEmail(savedLearner.getEmail(), savedLearner.getFullName(), savedLearner.getLearnerCode());
        }

        return mapToResponse(savedLearner);
    }

    @Transactional(readOnly = true)
    public LearnerResponse loginStudent(StudentLoginRequest request) {
        Learner learner = learnerRepository.findByLearnerCode(request.getStudentNumber())
                .orElseThrow(() -> new IllegalArgumentException("Student number or password is incorrect"));

        if (learner.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), learner.getPasswordHash())) {
            throw new IllegalArgumentException("Student number or password is incorrect");
        }
        return mapToResponse(learner);
    }

    @Transactional(readOnly = true)
    public List<StudentSubmissionHistoryDto> getLearnerSubmissions(String studentNumber) {
        // Verify learner exists
        if (!learnerRepository.existsByLearnerCode(studentNumber)) {
            throw new ResourceNotFoundException("Student number not found: " + studentNumber);
        }

        List<Submission> submissions = submissionRepository.findByLearner_LearnerCodeOrderBySubmittedAtDesc(studentNumber);
        return submissions.stream()
                .map(s -> StudentSubmissionHistoryDto.builder()
                        .submissionId(s.getId())
                        .sessionName(s.getSession() != null ? s.getSession().getSessionName() : "Assignment Window")
                        .assignmentTitle(s.getSession() != null && s.getSession().getAssignment() != null
                                ? s.getSession().getAssignment().getTitle()
                                : "Assignment")
                        .moduleName(s.getSession() != null && s.getSession().getAssignment() != null && s.getSession().getAssignment().getModule() != null
                                ? s.getSession().getAssignment().getModule().getModuleName()
                                : "General")
                        .originalFilename(s.getOriginalFilename())
                        .submittedAt(s.getSubmittedAt())
                        .status(s.getStatus().name())
                        .feedback(s.getFeedback())
                        .gradedAt(s.getGradedAt())
                        .gradedByRole(s.getGradedByRole())
                        .gradedByName(s.getGradedByName())
                        .marksAwarded(s.getMarksAwarded())
                        .markedFilePath(s.getMarkedFilePath())
                        .hasAnnotations(s.getAnnotationsJson() != null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LearnerSummaryResponse> getAllLearners() {
        return learnerRepository.findAll().stream()
                .map(learner -> LearnerSummaryResponse.builder()
                        .id(learner.getId())
                        .learnerCode(learner.getLearnerCode())
                        .fullName(learner.getFullName())
                        .cohort(learner.getCohort())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LearnerResponse getLearnerByCode(String learnerCode) {
        Learner learner = learnerRepository.findByLearnerCode(learnerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Learner not found with code: " + learnerCode));
        return mapToResponse(learner);
    }

    @Transactional
    public synchronized String generateNextLearnerCode() {
        int currentYear = Year.now().getValue();
        Optional<LearnerCodeSequence> sequenceOpt = sequenceRepository.findByYearVal(currentYear);

        LearnerCodeSequence sequence;
        if (sequenceOpt.isPresent()) {
            sequence = sequenceOpt.get();
            sequence.setLastSeq(sequence.getLastSeq() + 1);
        } else {
            sequence = LearnerCodeSequence.builder()
                    .yearVal(currentYear)
                    .lastSeq(1)
                    .build();
        }

        sequenceRepository.save(sequence);
        return String.format("%d%05d", currentYear, sequence.getLastSeq());
    }

    private LearnerResponse mapToResponse(Learner learner) {
        return LearnerResponse.builder()
                .id(learner.getId())
                .learnerCode(learner.getLearnerCode())
                .fullName(learner.getFullName())
                .email(learner.getEmail())
                .idNumber(learner.getIdNumber())
                .phoneNumber(learner.getPhoneNumber())
                .cohort(learner.getCohort())
                .learnershipId(learner.getLearnership() != null ? learner.getLearnership().getId() : null)
                .learnershipName(learner.getLearnership() != null ? learner.getLearnership().getName() : null)
                .createdAt(learner.getCreatedAt())
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Learner learner = learnerRepository.findByLearnerCode(request.getStudentNumber())
                .orElseThrow(() -> new IllegalArgumentException("Student number or email is incorrect"));

        if (learner.getEmail() == null || !learner.getEmail().trim().equalsIgnoreCase(request.getEmail().trim())) {
            throw new IllegalArgumentException("Student number or email is incorrect");
        }

        // Generate 6 digit reset code
        String resetCode = String.format("%06d", (int) (Math.random() * 900000) + 100000);
        learner.setResetCode(resetCode);
        learner.setResetCodeExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
        learnerRepository.save(learner);

        emailService.sendPasswordResetEmail(learner.getEmail(), learner.getFullName(), resetCode);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Learner learner = learnerRepository.findByLearnerCode(request.getStudentNumber())
                .orElseThrow(() -> new IllegalArgumentException("Student number or code is incorrect"));

        if (learner.getResetCode() == null || !learner.getResetCode().equals(request.getResetCode().trim())) {
            throw new IllegalArgumentException("Student number or code is incorrect");
        }

        if (learner.getResetCodeExpiresAt() == null || learner.getResetCodeExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset code has expired");
        }

        // Update password hash and invalidate code
        learner.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        learner.setResetCode(null);
        learner.setResetCodeExpiresAt(null);
        learnerRepository.save(learner);
    }
}
