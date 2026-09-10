package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.service.AuditLogService;
import com.example.learnerassignments.service.BackupService;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.service.CloudinaryService;
import com.example.learnerassignments.service.DeliveryHealthCheck;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final LecturerRepository lecturerRepository;
    private final ModeratorRepository moderatorRepository;
    private final AssessorRepository assessorRepository;
    private final ModuleRepository moduleRepository;
    private final CategoryRepository categoryRepository;
    private final LearnershipRepository learnershipRepository;
    private final SubmissionRepository submissionRepository;
    private final LearnerRepository learnerRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final AssessorAssignmentRepository assessorAssignmentRepository;
    private final ModeratorAssignmentRepository moderatorAssignmentRepository;
    private final com.example.learnerassignments.service.LearnerDocumentService learnerDocumentService;
    private final BackupService backupService;
    private final CloudinaryService cloudinaryService;
    private final DeliveryHealthCheck deliveryHealthCheck;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/lecturers")
    public ResponseEntity<AdminLecturerResponse> createLecturer(@Valid @RequestBody CreateLecturerRequest request) {
        if (lecturerRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Lecturer lecturer = Lecturer.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        Lecturer saved = lecturerRepository.save(lecturer);

        AdminLecturerResponse response = AdminLecturerResponse.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .username(saved.getUsername())
                .createdAt(saved.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/lecturers")
    public ResponseEntity<List<AdminLecturerResponse>> getAllLecturers() {
        List<AdminLecturerResponse> list = lecturerRepository.findAll().stream()
                .map(l -> AdminLecturerResponse.builder()
                        .id(l.getId())
                        .fullName(l.getFullName())
                        .email(l.getEmail())
                        .username(l.getUsername())
                        .createdAt(l.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/modules")
    public ResponseEntity<AdminModuleResponse> createModule(@Valid @RequestBody CreateModuleRequest request) {
        com.example.learnerassignments.model.Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        Module module = Module.builder()
                .moduleName(request.getModuleName())
                .moduleCode(request.getModuleCode())
                .category(category)
                .filePath(request.getFilePath())
                .build();

        Module saved = moduleRepository.save(module);

        AdminModuleResponse response = AdminModuleResponse.builder()
                .id(saved.getId())
                .moduleName(saved.getModuleName())
                .moduleCode(saved.getModuleCode())
                .lecturerId(saved.getCategory() != null && saved.getCategory().getLecturer() != null ? saved.getCategory().getLecturer().getId() : null)
                .lecturerName(saved.getCategory() != null && saved.getCategory().getLecturer() != null ? saved.getCategory().getLecturer().getFullName() : "Unassigned")
                .filePath(saved.getFilePath())
                .moduleType(saved.getCategory() != null ? saved.getCategory().getCategoryType() : "CORE")
                .files(java.util.Collections.emptyList())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/modules")
    public ResponseEntity<List<AdminModuleResponse>> getAllModules() {
        List<AdminModuleResponse> list = moduleRepository.findAll().stream()
                .map(m -> AdminModuleResponse.builder()
                        .id(m.getId())
                        .moduleName(m.getModuleName())
                        .moduleCode(m.getModuleCode())
                        .lecturerId(m.getCategory() != null && m.getCategory().getLecturer() != null ? m.getCategory().getLecturer().getId() : null)
                        .lecturerName(m.getCategory() != null && m.getCategory().getLecturer() != null ? m.getCategory().getLecturer().getFullName() : "Unassigned")
                        .filePath(m.getFilePath())
                        .moduleType(m.getCategory() != null ? m.getCategory().getCategoryType() : "CORE")
                        .files(m.getFiles() != null ? m.getFiles().stream()
                                .map(f -> com.example.learnerassignments.dto.ModuleFileDto.builder()
                                        .id(f.getId())
                                        .title(f.getTitle())
                                        .filePath(f.getFilePath())
                                        .originalFilename(f.getOriginalFilename())
                                        .fileType(f.getFileType())
                                        .build())
                                .collect(Collectors.toList()) : java.util.Collections.emptyList())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewResponse> getOverview() {
        long lecturersCount = lecturerRepository.count();
        long modulesCount = moduleRepository.count();
        long submissionsCount = submissionRepository.count();

        AdminOverviewResponse response = AdminOverviewResponse.builder()
                .lecturersCount(lecturersCount)
                .modulesCount(modulesCount)
                .submissionsCount(submissionsCount)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponseDto>> getAllCategories() {
        List<CategoryResponseDto> list = categoryRepository.findAll().stream()
                .map(c -> CategoryResponseDto.builder()
                        .id(c.getId())
                        .categoryType(c.getCategoryType())
                        .lecturerId(c.getLecturer() != null ? c.getLecturer().getId() : null)
                        .lecturerName(c.getLecturer() != null ? c.getLecturer().getFullName() : "Unassigned")
                        .learnershipId(c.getLearnership() != null ? c.getLearnership().getId() : null)
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PutMapping("/categories/{id}/assign-lecturer")
    public ResponseEntity<?> assignLecturer(
            @PathVariable Long id,
            @RequestBody AssignLecturerRequest request) {
        com.example.learnerassignments.model.Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Category not found"));

        if (request.getLecturerId() == null) {
            category.setLecturer(null);
        } else {
            Lecturer lecturer = lecturerRepository.findById(request.getLecturerId())
                    .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Lecturer not found"));
            category.setLecturer(lecturer);
        }

        categoryRepository.save(category);

        CategoryResponseDto response = CategoryResponseDto.builder()
                .id(category.getId())
                .categoryType(category.getCategoryType())
                .lecturerId(category.getLecturer() != null ? category.getLecturer().getId() : null)
                .lecturerName(category.getLecturer() != null ? category.getLecturer().getFullName() : "Unassigned")
                .learnershipId(category.getLearnership() != null ? category.getLearnership().getId() : null)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/learnerships")
    public ResponseEntity<LearnershipResponseDto> createLearnership(@RequestBody CreateLearnershipRequest request) {
        com.example.learnerassignments.model.Learnership learnership = com.example.learnerassignments.model.Learnership.builder()
                .name(request.getName())
                .qualificationCode(request.getQualificationCode())
                .build();
        com.example.learnerassignments.model.Learnership saved = learnershipRepository.save(learnership);
        LearnershipResponseDto dto = LearnershipResponseDto.builder()
                .id(saved.getId())
                .name(saved.getName())
                .qualificationCode(saved.getQualificationCode())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/learnerships")
    public ResponseEntity<List<LearnershipResponseDto>> getAdminLearnerships() {
        List<LearnershipResponseDto> list = learnershipRepository.findAll().stream()
                .map(l -> LearnershipResponseDto.builder()
                        .id(l.getId())
                        .name(l.getName())
                        .qualificationCode(l.getQualificationCode())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/learnerships/{id}/categories")
    public ResponseEntity<CategoryResponseDto> createCategory(
            @PathVariable Long id,
            @RequestBody CreateCategoryRequest request) {
        com.example.learnerassignments.model.Learnership learnership = learnershipRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Learnership not found"));

        com.example.learnerassignments.model.Category category = com.example.learnerassignments.model.Category.builder()
                .categoryType(request.getCategoryType())
                .learnership(learnership)
                .build();

        com.example.learnerassignments.model.Category saved = categoryRepository.save(category);

        CategoryResponseDto response = CategoryResponseDto.builder()
                .id(saved.getId())
                .categoryType(saved.getCategoryType())
                .learnershipId(learnership.getId())
                .lecturerId(null)
                .lecturerName("Unassigned")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/learners")
    public ResponseEntity<List<LearnerResponse>> getRegisteredLearners() {
        List<LearnerResponse> list = learnerRepository.findAll().stream()
                .map(l -> LearnerResponse.builder()
                        .id(l.getId())
                        .learnerCode(l.getLearnerCode())
                        .fullName(l.getFullName())
                        .email(l.getEmail())
                        .idNumber(l.getIdNumber())
                        .phoneNumber(l.getPhoneNumber())
                        .cohort(l.getCohort())
                        .learnershipId(l.getLearnership() != null ? l.getLearnership().getId() : null)
                        .learnershipName(l.getLearnership() != null ? l.getLearnership().getName() : "Unassigned")
                        .createdAt(l.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PutMapping("/learners/{id}")
    public ResponseEntity<LearnerResponse> updateLearner(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLearnerRequest request,
            Authentication auth) {
        com.example.learnerassignments.model.Learner learner = learnerRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Student not found"));

        learner.setFullName(request.getFullName());
        learner.setEmail(request.getEmail());
        learner.setIdNumber(request.getIdNumber());
        learner.setPhoneNumber(request.getPhoneNumber());
        learner.setCohort(request.getCohort());

        if (request.getLearnershipId() != null) {
            com.example.learnerassignments.model.Learnership learnership = learnershipRepository.findById(request.getLearnershipId())
                    .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Learnership not found"));
            learner.setLearnership(learnership);
        } else {
            learner.setLearnership(null);
        }

        com.example.learnerassignments.model.Learner saved = learnerRepository.save(learner);
        auditLogService.log(auth, "UPDATE_LEARNER", "Learner", id,
                "Student '" + saved.getFullName() + "' (" + saved.getLearnerCode() + ") profile updated");

        LearnerResponse response = LearnerResponse.builder()
                .id(saved.getId())
                .learnerCode(saved.getLearnerCode())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .idNumber(saved.getIdNumber())
                .phoneNumber(saved.getPhoneNumber())
                .cohort(saved.getCohort())
                .learnershipId(saved.getLearnership() != null ? saved.getLearnership().getId() : null)
                .learnershipName(saved.getLearnership() != null ? saved.getLearnership().getName() : "Unassigned")
                .createdAt(saved.getCreatedAt())
                .build();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/learners/{id}/reset-password")
    public ResponseEntity<Void> adminResetPassword(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        com.example.learnerassignments.model.Learner learner = learnerRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Student not found"));

        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }

        learner.setPasswordHash(passwordEncoder.encode(newPassword));
        learner.setResetCode(null);
        learner.setResetCodeExpiresAt(null);
        learnerRepository.save(learner);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/learners/{id}")
    public ResponseEntity<Void> deleteLearner(@PathVariable Long id, Authentication auth) {
        com.example.learnerassignments.model.Learner learner = learnerRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Student not found"));
        learnerRepository.deleteById(id);
        auditLogService.log(auth, "DELETE_LEARNER", "Learner", id,
                "Student '" + learner.getFullName() + "' (" + learner.getLearnerCode() + ") deleted");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/settings/registration-status")
    public ResponseEntity<java.util.Map<String, Boolean>> getRegistrationStatus() {
        boolean open = systemSettingRepository.findById("REGISTRATION_OPEN")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);
        return ResponseEntity.ok(java.util.Map.of("open", open));
    }

    @PostMapping("/settings/registration-status/toggle")
    public ResponseEntity<java.util.Map<String, Boolean>> toggleRegistrationStatus() {
        com.example.learnerassignments.model.SystemSetting setting = systemSettingRepository.findById("REGISTRATION_OPEN")
                .orElse(com.example.learnerassignments.model.SystemSetting.builder()
                        .settingKey("REGISTRATION_OPEN")
                        .settingValue("true")
                        .build());
        
        boolean currentlyOpen = "true".equalsIgnoreCase(setting.getSettingValue());
        boolean nextOpen = !currentlyOpen;
        setting.setSettingValue(String.valueOf(nextOpen));
        systemSettingRepository.save(setting);

        return ResponseEntity.ok(java.util.Map.of("open", nextOpen));
    }

    @DeleteMapping("/lecturers/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteLecturer(@PathVariable Long id, Authentication auth) {
        Lecturer lecturer = lecturerRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Lecturer not found"));

        // 1. Unassign lecturer from all categories
        List<com.example.learnerassignments.model.Category> categories = categoryRepository.findByLecturerId(id);
        for (com.example.learnerassignments.model.Category category : categories) {
            category.setLecturer(null);
            categoryRepository.save(category);
        }

        // 2. Delete the lecturer
        lecturerRepository.delete(lecturer);
        auditLogService.log(auth, "DELETE_LECTURER", "Lecturer", id,
                "Facilitator '" + lecturer.getFullName() + "' (" + lecturer.getUsername() + ") deleted");

        return ResponseEntity.ok().build();
    }

    // --- LECTURER UPDATE ---
    @PutMapping("/lecturers/{id}")
    public ResponseEntity<AdminLecturerResponse> updateLecturer(
            @PathVariable Long id,
            @Valid @RequestBody CreateLecturerRequest request) {
        Lecturer lecturer = lecturerRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Lecturer not found"));

        if (!lecturer.getUsername().equals(request.getUsername()) &&
            lecturerRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        lecturer.setFullName(request.getFullName());
        lecturer.setEmail(request.getEmail());
        lecturer.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            lecturer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        Lecturer saved = lecturerRepository.save(lecturer);
        AdminLecturerResponse response = AdminLecturerResponse.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .username(saved.getUsername())
                .createdAt(saved.getCreatedAt())
                .build();
        return ResponseEntity.ok(response);
    }

    // --- MODERATORS CRUD ---
    @GetMapping("/moderators")
    public ResponseEntity<List<AdminModeratorResponse>> getAllModerators() {
        List<AdminModeratorResponse> list = moderatorRepository.findAll().stream()
                .map(m -> AdminModeratorResponse.builder()
                        .id(m.getId())
                        .fullName(m.getFullName())
                        .email(m.getEmail())
                        .username(m.getUsername())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/moderators")
    public ResponseEntity<AdminModeratorResponse> createModerator(@Valid @RequestBody CreateModeratorRequest request) {
        if (moderatorRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        com.example.learnerassignments.model.Moderator moderator = com.example.learnerassignments.model.Moderator.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        com.example.learnerassignments.model.Moderator saved = moderatorRepository.save(moderator);
        AdminModeratorResponse response = AdminModeratorResponse.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .username(saved.getUsername())
                .createdAt(saved.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/moderators/{id}")
    public ResponseEntity<AdminModeratorResponse> updateModerator(
            @PathVariable Long id,
            @Valid @RequestBody CreateModeratorRequest request) {
        com.example.learnerassignments.model.Moderator moderator = moderatorRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Moderator not found"));

        if (!moderator.getUsername().equals(request.getUsername()) &&
            moderatorRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        moderator.setFullName(request.getFullName());
        moderator.setEmail(request.getEmail());
        moderator.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            moderator.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        com.example.learnerassignments.model.Moderator saved = moderatorRepository.save(moderator);
        AdminModeratorResponse response = AdminModeratorResponse.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .username(saved.getUsername())
                .createdAt(saved.getCreatedAt())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/moderators/{id}")
    public ResponseEntity<Void> deleteModerator(@PathVariable Long id, Authentication auth) {
        com.example.learnerassignments.model.Moderator moderator = moderatorRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Moderator not found"));
        moderatorRepository.deleteById(id);
        auditLogService.log(auth, "DELETE_MODERATOR", "Moderator", id,
                "Moderator '" + moderator.getFullName() + "' (" + moderator.getUsername() + ") deleted");
        return ResponseEntity.ok().build();
    }

    // --- ASSESSORS CRUD ---
    @GetMapping("/assessors")
    public ResponseEntity<List<AdminAssessorResponse>> getAllAssessors() {
        List<AdminAssessorResponse> list = assessorRepository.findAll().stream()
                .map(a -> AdminAssessorResponse.builder()
                        .id(a.getId())
                        .fullName(a.getFullName())
                        .email(a.getEmail())
                        .username(a.getUsername())
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/assessors")
    public ResponseEntity<AdminAssessorResponse> createAssessor(@Valid @RequestBody CreateAssessorRequest request) {
        if (assessorRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        com.example.learnerassignments.model.Assessor assessor = com.example.learnerassignments.model.Assessor.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        com.example.learnerassignments.model.Assessor saved = assessorRepository.save(assessor);
        AdminAssessorResponse response = AdminAssessorResponse.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .username(saved.getUsername())
                .createdAt(saved.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/assessors/{id}")
    public ResponseEntity<AdminAssessorResponse> updateAssessor(
            @PathVariable Long id,
            @Valid @RequestBody CreateAssessorRequest request) {
        com.example.learnerassignments.model.Assessor assessor = assessorRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Assessor not found"));

        if (!assessor.getUsername().equals(request.getUsername()) &&
            assessorRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        assessor.setFullName(request.getFullName());
        assessor.setEmail(request.getEmail());
        assessor.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            assessor.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        com.example.learnerassignments.model.Assessor saved = assessorRepository.save(assessor);
        AdminAssessorResponse response = AdminAssessorResponse.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .username(saved.getUsername())
                .createdAt(saved.getCreatedAt())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/assessors/{id}")
    public ResponseEntity<Void> deleteAssessor(@PathVariable Long id, Authentication auth) {
        com.example.learnerassignments.model.Assessor assessor = assessorRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Assessor not found"));
        assessorRepository.deleteById(id);
        auditLogService.log(auth, "DELETE_ASSESSOR", "Assessor", id,
                "Assessor '" + assessor.getFullName() + "' (" + assessor.getUsername() + ") deleted");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<com.example.learnerassignments.model.AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/backups/run")
    public ResponseEntity<?> runBackup(Authentication auth) {
        try {
            String filename = backupService.performBackup();
            auditLogService.log(auth, "RUN_BACKUP", "Backup", null, "Manual backup triggered: " + filename);
            return ResponseEntity.ok(java.util.Map.of("filename", filename));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("message", "Backup failed: " + e.getMessage()));
        }
    }

    @GetMapping("/backups")
    public ResponseEntity<?> listBackups() {
        try {
            List<java.util.Map<String, Object>> resources = cloudinaryService.listBackups();
            List<java.util.Map<String, Object>> withUrls = resources.stream()
                    .map(r -> {
                        java.util.Map<String, Object> entry = new java.util.HashMap<>(r);
                        entry.put("downloadUrl", cloudinaryService.getSignedBackupUrl((String) r.get("public_id")));
                        return entry;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(withUrls);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("message", "Failed to list backups: " + e.getMessage()));
        }
    }

    /**
     * Runs the storage delivery probe on demand and reports what happened.
     *
     * Diagnosing a storage configuration problem otherwise costs a redeploy and a cold start
     * per attempt, with the answer only readable in the hosting platform's log viewer. This
     * does the same upload-fetch-compare-delete as the boot check and returns the result,
     * including the storage provider's own error text — which is the only part worth reading,
     * and which the vault's learner-facing message deliberately does not show.
     *
     * Admin-only, because that error text is internal detail. It never contains a signed URL.
     */
    @PostMapping("/diagnostics/storage")
    public ResponseEntity<?> checkStorage() {
        DeliveryHealthCheck.Result result = deliveryHealthCheck.checkNow();
        return ResponseEntity.ok(java.util.Map.of(
                "ok", result.ok(),
                "step", result.step(),
                "detail", result.detail()));
    }

    /**
     * What the submissions table actually contains, for the two questions this build raised
     * that could not be answered from outside: how many submissions the grading console used
     * to hide, and how the stored file paths are shaped.
     *
     * Read-only. It exists because Render's free plan has no shell, so there is otherwise no
     * way to run a query against production at all.
     */
    @GetMapping("/diagnostics/submissions")
    public ResponseEntity<?> submissionDiagnostics() {
        long total = submissionRepository.count();
        long missingFromRoster = submissionRepository.countMissingFromRoster();
        long legacyUrls = submissionRepository.countLegacyUrlPaths();
        long authenticated = submissionRepository.countAuthenticatedPublicIds();

        List<java.util.Map<String, Object>> sample = submissionRepository
                .findMissingFromRoster(org.springframework.data.domain.PageRequest.of(0, 20))
                .stream()
                .map(s -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("submissionId", s.getId());
                    row.put("learnerCode", s.getLearner() == null ? null : s.getLearner().getLearnerCode());
                    row.put("learnerName", s.getLearner() == null ? null : s.getLearner().getFullName());
                    row.put("session", s.getSession() == null ? null : s.getSession().getSessionName());
                    row.put("submittedAt", s.getSubmittedAt());
                    row.put("graded", s.getGradedAt() != null);
                    // Deliberately not the file path: this is a diagnostic, not a way to hand
                    // out storage locations.
                    return row;
                })
                .collect(Collectors.toList());

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("totalSubmissions", total);
        body.put("missingFromRoster", missingFromRoster);
        body.put("missingFromRosterUngraded", sample.stream().filter(r -> !((Boolean) r.get("graded"))).count());
        body.put("storageShapes", java.util.Map.of(
                "legacyPublicUrl", legacyUrls,
                "authenticatedPublicId", authenticated,
                "diskPath", total - legacyUrls - authenticated));
        body.put("sample", sample);
        body.put("note", "missingFromRoster counts submissions whose learner has no learner_modules "
                + "row for the session's module. Before the grading console stopped deriving its "
                + "list from the roster, these were invisible to whoever had to mark them.");
        return ResponseEntity.ok(body);
    }

    // --- MARKING BACKLOG ---

    @GetMapping("/marking-backlog")
    public ResponseEntity<List<MarkingBacklogEntryDto>> getMarkingBacklog() {
        // Facilitators: real per-lecturer counts via FK chain pushed into the DB query.
        List<MarkingBacklogEntryDto> result = new ArrayList<>(submissionRepository.findFacilitatorMarkingBacklog());

        // Moderators and assessors have no FK chain to individual submissions in this schema,
        // so they are included with zero counts to confirm their presence in the system.
        moderatorRepository.findAll().forEach(m ->
            result.add(new MarkingBacklogEntryDto(m.getId(), m.getFullName(), "MODERATOR", 0L, 0L, null))
        );
        assessorRepository.findAll().forEach(a ->
            result.add(new MarkingBacklogEntryDto(a.getId(), a.getFullName(), "ASSESSOR", 0L, 0L, null))
        );

        return ResponseEntity.ok(result);
    }

    // --- Assessor and moderator scoping ---
    //
    // Without rows here an assessor or moderator reaches nothing, which is deliberate: a new
    // account should not be able to read the cohort before someone decides what it may see.
    // These are the screens that make that decision.

    @GetMapping("/assessors/{assessorId}/assignments")
    public ResponseEntity<List<AssignmentScopeDtos.AssessorAssignmentResponse>> listAssessorAssignments(
            @PathVariable Long assessorId) {
        return ResponseEntity.ok(assessorAssignmentRepository.findByAssessor_Id(assessorId).stream()
                .map(this::toAssessorAssignmentResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PostMapping("/assessors/{assessorId}/assignments")
    public ResponseEntity<AssignmentScopeDtos.AssessorAssignmentResponse> createAssessorAssignment(
            @PathVariable Long assessorId,
            @RequestBody AssignmentScopeDtos.CreateAssessorAssignmentRequest request,
            Authentication auth) {

        com.example.learnerassignments.model.Assessor assessor = assessorRepository.findById(assessorId)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Assessor not found"));

        boolean hasLearner = request.getLearnerId() != null;
        boolean hasCategory = request.getCategoryId() != null;
        if (hasLearner == hasCategory) {
            // Both would be ambiguous; neither would be a row that grants nothing while
            // looking like a grant, which is worse than no row at all.
            throw new IllegalArgumentException("Give either a learner or a category, not both and not neither.");
        }

        com.example.learnerassignments.model.AssessorAssignment assignment =
                com.example.learnerassignments.model.AssessorAssignment.builder()
                        .assessor(assessor)
                        .learner(hasLearner ? learnerRepository.findById(request.getLearnerId())
                                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Student not found")) : null)
                        .category(hasCategory ? categoryRepository.findById(request.getCategoryId())
                                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Category not found")) : null)
                        .build();

        var saved = assessorAssignmentRepository.save(assignment);
        auditLogService.log(auth, "CREATE_ASSESSOR_ASSIGNMENT", "AssessorAssignment", saved.getId(),
                "Assessor " + assessor.getFullName() + " granted access to "
                        + (hasLearner ? "learner " + request.getLearnerId()
                                      : "category " + request.getCategoryId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toAssessorAssignmentResponse(saved));
    }

    @DeleteMapping("/assessors/{assessorId}/assignments/{assignmentId}")
    public ResponseEntity<Void> deleteAssessorAssignment(
            @PathVariable Long assessorId, @PathVariable Long assignmentId, Authentication auth) {
        var assignment = assessorAssignmentRepository.findById(assignmentId)
                .filter(a -> a.getAssessor().getId().equals(assessorId))
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Assignment not found"));
        assessorAssignmentRepository.delete(assignment);
        auditLogService.log(auth, "DELETE_ASSESSOR_ASSIGNMENT", "AssessorAssignment", assignmentId,
                "Assessor access revoked");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/moderators/{moderatorId}/assignments")
    public ResponseEntity<List<AssignmentScopeDtos.ModeratorAssignmentResponse>> listModeratorAssignments(
            @PathVariable Long moderatorId) {
        return ResponseEntity.ok(moderatorAssignmentRepository.findByModerator_Id(moderatorId).stream()
                .map(this::toModeratorAssignmentResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PostMapping("/moderators/{moderatorId}/assignments")
    public ResponseEntity<AssignmentScopeDtos.ModeratorAssignmentResponse> createModeratorAssignment(
            @PathVariable Long moderatorId,
            @Valid @RequestBody AssignmentScopeDtos.CreateModeratorAssignmentRequest request,
            Authentication auth) {

        com.example.learnerassignments.model.Moderator moderator = moderatorRepository.findById(moderatorId)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Moderator not found"));
        var learnership = learnershipRepository.findById(request.getLearnershipId())
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Learnership not found"));

        com.example.learnerassignments.model.ModerationScope scope =
                com.example.learnerassignments.model.ModerationScope.FULL;
        if (request.getScope() != null && !request.getScope().isBlank()) {
            try {
                scope = com.example.learnerassignments.model.ModerationScope.valueOf(request.getScope().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Scope must be FULL or SAMPLE.");
            }
        }

        String cohort = (request.getCohort() == null || request.getCohort().isBlank()) ? null : request.getCohort().trim();

        var saved = moderatorAssignmentRepository.save(
                com.example.learnerassignments.model.ModeratorAssignment.builder()
                        .moderator(moderator).learnership(learnership).cohort(cohort).scope(scope).build());

        auditLogService.log(auth, "CREATE_MODERATOR_ASSIGNMENT", "ModeratorAssignment", saved.getId(),
                "Moderator " + moderator.getFullName() + " granted " + scope
                        + " access to learnership " + learnership.getName()
                        + (cohort != null ? " cohort " + cohort : " (all cohorts)"));
        return ResponseEntity.status(HttpStatus.CREATED).body(toModeratorAssignmentResponse(saved));
    }

    @DeleteMapping("/moderators/{moderatorId}/assignments/{assignmentId}")
    public ResponseEntity<Void> deleteModeratorAssignment(
            @PathVariable Long moderatorId, @PathVariable Long assignmentId, Authentication auth) {
        var assignment = moderatorAssignmentRepository.findById(assignmentId)
                .filter(a -> a.getModerator().getId().equals(moderatorId))
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Assignment not found"));
        moderatorAssignmentRepository.delete(assignment);
        auditLogService.log(auth, "DELETE_MODERATOR_ASSIGNMENT", "ModeratorAssignment", assignmentId,
                "Moderator access revoked");
        return ResponseEntity.noContent().build();
    }

    private AssignmentScopeDtos.AssessorAssignmentResponse toAssessorAssignmentResponse(
            com.example.learnerassignments.model.AssessorAssignment a) {
        return AssignmentScopeDtos.AssessorAssignmentResponse.builder()
                .id(a.getId())
                .assessorId(a.getAssessor().getId())
                .assessorName(a.getAssessor().getFullName())
                .learnerId(a.getLearner() != null ? a.getLearner().getId() : null)
                .learnerName(a.getLearner() != null ? a.getLearner().getFullName() : null)
                .learnerCode(a.getLearner() != null ? a.getLearner().getLearnerCode() : null)
                .categoryId(a.getCategory() != null ? a.getCategory().getId() : null)
                .categoryType(a.getCategory() != null ? a.getCategory().getCategoryType() : null)
                .assignedAt(a.getAssignedAt())
                .build();
    }

    private AssignmentScopeDtos.ModeratorAssignmentResponse toModeratorAssignmentResponse(
            com.example.learnerassignments.model.ModeratorAssignment a) {
        return AssignmentScopeDtos.ModeratorAssignmentResponse.builder()
                .id(a.getId())
                .moderatorId(a.getModerator().getId())
                .moderatorName(a.getModerator().getFullName())
                .learnershipId(a.getLearnership().getId())
                .learnershipName(a.getLearnership().getName())
                .cohort(a.getCohort())
                .scope(a.getScope() != null ? a.getScope().name() : null)
                .assignedAt(a.getAssignedAt())
                .build();
    }

    // --- Learner documents (Phase 3) ---
    //
    // Review only. There is deliberately no endpoint here for uploading a document on a
    // learner's behalf: learners supply their own, which is what removes the roughly hundred
    // and sixty manual uploads a forty-learner cohort would otherwise cost.

    @GetMapping("/learners/{learnerId}/documents")
    public ResponseEntity<List<LearnerDocumentDtos.DocumentResponse>> listLearnerDocuments(
            @PathVariable Long learnerId) {
        learnerRepository.findById(learnerId)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Student not found"));
        return ResponseEntity.ok(learnerDocumentService.listFor(learnerId).stream()
                .map(this::toLearnerDocumentResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PutMapping("/documents/{documentId}/review")
    public ResponseEntity<LearnerDocumentDtos.DocumentResponse> reviewLearnerDocument(
            @PathVariable Long documentId,
            @RequestBody LearnerDocumentDtos.ReviewDocumentRequest request,
            Authentication auth) {

        com.example.learnerassignments.model.ReviewStatus status;
        try {
            status = com.example.learnerassignments.model.ReviewStatus.valueOf(
                    String.valueOf(request.getStatus()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("A review decision must be ACCEPTED or REJECTED.");
        }

        var reviewed = learnerDocumentService.review(
                documentId, status, request.getNote(), auth != null ? auth.getName() : null);

        auditLogService.log(auth, "REVIEW_LEARNER_DOCUMENT", "LearnerDocument", documentId,
                status + (reviewed.getReviewNote() != null ? ": " + reviewed.getReviewNote() : ""));
        return ResponseEntity.ok(toLearnerDocumentResponse(reviewed));
    }

    /**
     * A learner's document, for the reviewer who has to look at it before deciding.
     *
     * Admin-only by the security rules, and streamed rather than linked, so the file is never
     * reachable except through a request this application has authorised.
     */
    @GetMapping("/documents/{documentId}/view")
    public ResponseEntity<?> viewLearnerDocument(@PathVariable Long documentId) {
        var document = learnerDocumentService.require(documentId);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        learnerDocumentService.resolveContentType(document.getOriginalFilename())))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getOriginalFilename() + "\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .body(learnerDocumentService.load(document));
    }

    private LearnerDocumentDtos.DocumentResponse toLearnerDocumentResponse(
            com.example.learnerassignments.model.LearnerDocument d) {
        return LearnerDocumentDtos.DocumentResponse.builder()
                .id(d.getId())
                .documentType(d.getDocumentType().name())
                .documentLabel(d.getDocumentType().getLabel())
                .poeSection(d.getDocumentType().getPoeSection())
                .required(d.getDocumentType().isRequired())
                .originalFilename(d.getOriginalFilename())
                .version(d.getVersion())
                // Not superseded, rather than current = true: a null would drop the row and
                // read as a document that was never supplied.
                .current(!Boolean.FALSE.equals(d.getCurrent()))
                .status(d.getStatus() == null
                        ? com.example.learnerassignments.model.ReviewStatus.PENDING.name()
                        : d.getStatus().name())
                .reviewNote(d.getReviewNote())
                .reviewedAt(d.getReviewedAt())
                .uploadedAt(d.getUploadedAt())
                .uploadedByRole(d.getUploadedByRole())
                .build();
    }
}
