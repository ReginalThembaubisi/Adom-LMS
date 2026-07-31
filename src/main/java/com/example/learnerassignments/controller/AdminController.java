package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final LecturerRepository lecturerRepository;
    private final ModuleRepository moduleRepository;
    private final CategoryRepository categoryRepository;
    private final LearnershipRepository learnershipRepository;
    private final SubmissionRepository submissionRepository;
    private final LearnerRepository learnerRepository;
    private final SystemSettingRepository systemSettingRepository;
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
    public ResponseEntity<Void> deleteLearner(@PathVariable Long id) {
        if (!learnerRepository.existsById(id)) {
            throw new com.example.learnerassignments.exception.ResourceNotFoundException("Student not found");
        }
        learnerRepository.deleteById(id);
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
}
