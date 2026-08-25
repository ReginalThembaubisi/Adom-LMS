package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.service.AuditLogService;
import com.example.learnerassignments.service.BackupService;
import com.example.learnerassignments.service.CloudinaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
    private final BackupService backupService;
    private final CloudinaryService cloudinaryService;
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
}
