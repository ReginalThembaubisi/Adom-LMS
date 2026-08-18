package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.service.SubmissionSessionService;
import com.example.learnerassignments.service.SubmissionService;
import com.example.learnerassignments.service.CloudinaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lecturer")
@RequiredArgsConstructor
public class LecturerController {

    private final LecturerRepository lecturerRepository;
    private final ModuleRepository moduleRepository;
    private final CategoryRepository categoryRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    private final ModuleFileRepository moduleFileRepository;
    private final SubmissionSessionService sessionService;
    private final SubmissionService submissionService;
    private final CloudinaryService cloudinaryService;
    private final com.example.learnerassignments.service.AuditLogService auditLogService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final String UPLOAD_DIR = "uploads/";

    private Lecturer getAuthenticatedLecturer(Authentication auth) {
        if (auth == null) {
            throw new RuntimeException("Unauthorized");
        }
        return lecturerRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Lecturer record not found"));
    }

    @GetMapping("/modules")
    public ResponseEntity<List<AdminModuleResponse>> getMyModules(Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        List<AdminModuleResponse> list = moduleRepository.findByCategoryLecturerId(lecturer.getId()).stream()
                .map(m -> AdminModuleResponse.builder()
                        .id(m.getId())
                        .moduleName(m.getModuleName())
                        .moduleCode(m.getModuleCode())
                        .lecturerId(lecturer.getId())
                        .lecturerName(lecturer.getFullName())
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

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getMySessions(Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        List<Long> myModuleIds = moduleRepository.findByCategoryLecturerId(lecturer.getId()).stream()
                .map(Module::getId)
                .collect(Collectors.toList());

        List<SessionResponse> filtered = sessionRepository.findAll().stream()
                .filter(ss -> ss.getAssignment() != null &&
                        ss.getAssignment().getModule() != null &&
                        myModuleIds.contains(ss.getAssignment().getModule().getId()))
                .map(ss -> SessionResponse.builder()
                        .id(ss.getId())
                        .sessionName(ss.getSessionName())
                        .assignmentId(ss.getAssignment().getId())
                        .assignmentTitle("[" + ss.getAssignment().getModule().getModuleName() + "] " + ss.getAssignment().getTitle())
                        .startTime(ss.getStartTime())
                        .endTime(ss.getEndTime())
                        .status(ss.getStatus())
                        .createdAt(ss.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    @PostMapping("/modules/{id}/upload")
    public ResponseEntity<AdminModuleResponse> uploadModuleFile(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType,
            @RequestParam("title") String title,
            Authentication auth) throws IOException {
        
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Module not found"));

        if (module.getCategory() == null || module.getCategory().getLecturer() == null || !module.getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Save file (Cloudinary if configured, otherwise local disk fallback)
        String fileUrl;
        if (cloudinaryService.isConfigured()) {
            fileUrl = cloudinaryService.uploadFile(file);
        } else {
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();

            String uniqueFilename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = Paths.get(UPLOAD_DIR, uniqueFilename);
            Files.copy(file.getInputStream(), filePath);

            fileUrl = "/" + UPLOAD_DIR + uniqueFilename;
        }

        // Save new ModuleFile record
        ModuleFile moduleFile = ModuleFile.builder()
                .module(module)
                .title(title)
                .filePath(fileUrl)
                .originalFilename(file.getOriginalFilename())
                .fileType(fileType)
                .build();
        moduleFileRepository.save(moduleFile);

        // Keep updating legacy filePath for "Syllabus" files
        if ("Syllabus".equalsIgnoreCase(fileType)) {
            module.setFilePath(fileUrl);
            moduleRepository.save(module);
        }

        // Fetch refreshed module to get files list
        Module refreshedModule = moduleRepository.findById(id).orElse(module);

        AdminModuleResponse response = AdminModuleResponse.builder()
                .id(refreshedModule.getId())
                .moduleName(refreshedModule.getModuleName())
                .moduleCode(refreshedModule.getModuleCode())
                .lecturerId(lecturer.getId())
                .lecturerName(lecturer.getFullName())
                .filePath(refreshedModule.getFilePath())
                .moduleType(refreshedModule.getCategory() != null ? refreshedModule.getCategory().getCategoryType() : "CORE")
                .files(refreshedModule.getFiles() != null ? refreshedModule.getFiles().stream()
                        .map(f -> com.example.learnerassignments.dto.ModuleFileDto.builder()
                                .id(f.getId())
                                .title(f.getTitle())
                                .filePath(f.getFilePath())
                                .originalFilename(f.getOriginalFilename())
                                .fileType(f.getFileType())
                                .build())
                        .collect(Collectors.toList()) : java.util.Collections.emptyList())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/sessions", consumes = {"multipart/form-data"})
    public ResponseEntity<?> createSession(
            @RequestParam("sessionName") String sessionName,
            @RequestParam("moduleId") Long moduleId,
            @RequestParam("startTime") String startTimeStr,
            @RequestParam("endTime") String endTimeStr,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication auth) throws IOException {

        Lecturer lecturer = getAuthenticatedLecturer(auth);

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));

        if (module.getCategory() == null || module.getCategory().getLecturer() == null ||
            !module.getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own this module");
        }

        java.time.LocalDateTime startTime = java.time.LocalDateTime.parse(startTimeStr);
        java.time.LocalDateTime endTime = java.time.LocalDateTime.parse(endTimeStr);

        Assignment assignment = Assignment.builder()
                .title(sessionName)
                .description(description != null ? description : "")
                .dueDate(endTime)
                .module(module)
                .build();
        Assignment savedAssignment = assignmentRepository.save(assignment);

        String relativePath = null;
        String originalFilename = null;
        if (file != null && !file.isEmpty()) {
            if (cloudinaryService.isConfigured()) {
                relativePath = cloudinaryService.uploadFile(file);
            } else {
                File dir = new File(UPLOAD_DIR);
                if (!dir.exists()) dir.mkdirs();

                String uniqueFilename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                Path filePath = Paths.get(UPLOAD_DIR, uniqueFilename);
                Files.copy(file.getInputStream(), filePath);

                relativePath = "/" + UPLOAD_DIR + uniqueFilename;
            }
            originalFilename = file.getOriginalFilename();
        }

        SubmissionSession session = SubmissionSession.builder()
                .sessionName(sessionName)
                .assignment(savedAssignment)
                .startTime(startTime)
                .endTime(endTime)
                .status(SessionStatus.SCHEDULED)
                .taskFilePath(relativePath)
                .taskFileName(originalFilename)
                .build();

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (now.isAfter(startTime) && now.isBefore(endTime)) {
            session.setStatus(SessionStatus.OPEN);
        } else if (now.isAfter(endTime)) {
            session.setStatus(SessionStatus.CLOSED);
        }

        SubmissionSession savedSession = sessionRepository.save(session);

        SessionResponse response = SessionResponse.builder()
                .id(savedSession.getId())
                .sessionName(savedSession.getSessionName())
                .assignmentId(savedAssignment.getId())
                .assignmentTitle(savedAssignment.getTitle())
                .startTime(savedSession.getStartTime())
                .endTime(savedSession.getEndTime())
                .status(savedSession.getStatus())
                .createdAt(savedSession.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/sessions/{id}/close")
    public ResponseEntity<SessionResponse> closeSession(@PathVariable Long id, Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        SubmissionSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        
        if (session.getAssignment() == null || session.getAssignment().getModule() == null ||
            session.getAssignment().getModule().getCategory() == null ||
            session.getAssignment().getModule().getCategory().getLecturer() == null ||
            !session.getAssignment().getModule().getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        SessionResponse response = sessionService.manuallyCloseSession(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/sessions/{id}/open")
    public ResponseEntity<SessionResponse> openSession(@PathVariable Long id, Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        SubmissionSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getAssignment() == null || session.getAssignment().getModule() == null ||
            session.getAssignment().getModule().getCategory() == null ||
            session.getAssignment().getModule().getCategory().getLecturer() == null ||
            !session.getAssignment().getModule().getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        SessionResponse response = sessionService.manuallyOpenSession(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{id}/submissions")
    public ResponseEntity<SessionSubmissionOverviewResponse> getSessionSubmissions(
            @PathVariable Long id,
            Authentication auth) {
        
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        SubmissionSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getAssignment() == null || session.getAssignment().getModule() == null ||
            session.getAssignment().getModule().getCategory() == null ||
            session.getAssignment().getModule().getCategory().getLecturer() == null ||
            !session.getAssignment().getModule().getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        SessionSubmissionOverviewResponse overview = submissionService.getSessionSubmissionsOverview(id);
        return ResponseEntity.ok(overview);
    }

    @PutMapping("/submissions/{id}/grade")
    public ResponseEntity<Void> gradeSubmission(
            @PathVariable Long id,
            @RequestParam("grade") Integer grade,
            @RequestParam(value = "feedback", required = false) String feedback,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication auth) throws IOException {
        
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        // Scope check
        SubmissionSession session = submission.getSession();
        if (session == null || session.getAssignment() == null ||
            session.getAssignment().getModule() == null ||
            session.getAssignment().getModule().getCategory() == null ||
            session.getAssignment().getModule().getCategory().getLecturer() == null ||
            !session.getAssignment().getModule().getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        submission.setGrade(grade);
        submission.setFeedback(feedback);
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(java.time.LocalDateTime.now());

        // Handle optional marked-up file upload
        if (file != null && !file.isEmpty()) {
            String gradedUrl;
            if (cloudinaryService.isConfigured()) {
                gradedUrl = cloudinaryService.uploadFile(file);
            } else {
                File dir = new File(UPLOAD_DIR);
                if (!dir.exists()) dir.mkdirs();

                String uniqueFilename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                Path filePath = Paths.get(UPLOAD_DIR, uniqueFilename);
                Files.copy(file.getInputStream(), filePath);

                gradedUrl = "/" + UPLOAD_DIR + uniqueFilename;
            }

            submission.setGradedFilePath(gradedUrl);
            submission.setGradedOriginalFilename(file.getOriginalFilename());
        }

        submissionRepository.save(submission);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponseDto>> getMyCategories(Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        List<CategoryResponseDto> list = categoryRepository.findByLecturerId(lecturer.getId()).stream()
                .map(c -> CategoryResponseDto.builder()
                        .id(c.getId())
                        .categoryType(c.getCategoryType())
                        .lecturerId(c.getLecturer() != null ? c.getLecturer().getId() : null)
                        .lecturerName(c.getLecturer() != null ? c.getLecturer().getFullName() : "Unassigned")
                        .learnershipId(c.getLearnership() != null ? c.getLearnership().getId() : null)
                        .learnershipName(c.getLearnership() != null ? c.getLearnership().getName() : "Unknown")
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/profile")
    public ResponseEntity<LecturerProfileResponseDto> getMyProfile(Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        List<String> assignedCategories = categoryRepository.findByLecturerId(lecturer.getId()).stream()
                .map(c -> {
                    String type = c.getCategoryType().substring(0, 1).toUpperCase() + c.getCategoryType().substring(1).toLowerCase();
                    return type + " Category" + (c.getLearnership() != null ? " (" + c.getLearnership().getName() + ")" : "");
                })
                .collect(Collectors.toList());

        LecturerProfileResponseDto dto = LecturerProfileResponseDto.builder()
                .id(lecturer.getId())
                .fullName(lecturer.getFullName())
                .email(lecturer.getEmail())
                .username(lecturer.getUsername())
                .assignedCategories(assignedCategories)
                .build();
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/modules")
    public ResponseEntity<?> createModule(Authentication auth, @Valid @RequestBody CreateModuleRequest request) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);

        if (request.getCategoryId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Category ID is required");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Category not found"));

        if (category.getLecturer() == null || !category.getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not assigned to this category");
        }

        Module module = Module.builder()
                .moduleName(request.getModuleName())
                .moduleCode(request.getModuleCode())
                .category(category)
                .build();

        Module saved = moduleRepository.save(module);

        AdminModuleResponse response = AdminModuleResponse.builder()
                .id(saved.getId())
                .moduleName(saved.getModuleName())
                .moduleCode(saved.getModuleCode())
                .lecturerId(lecturer.getId())
                .lecturerName(lecturer.getFullName())
                .filePath(null)
                .moduleType(category.getCategoryType())
                .files(java.util.Collections.emptyList())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/modules/{id}")
    public ResponseEntity<?> updateModule(
            @PathVariable Long id,
            @Valid @RequestBody CreateModuleRequest request,
            Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Module not found"));

        if (module.getCategory() == null || module.getCategory().getLecturer() == null ||
            !module.getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own this module");
        }

        if (request.getCategoryId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Category ID is required");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Category not found"));

        if (category.getLecturer() == null || !category.getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not assigned to this category");
        }

        module.setModuleName(request.getModuleName());
        module.setModuleCode(request.getModuleCode());
        module.setCategory(category);
        Module saved = moduleRepository.save(module);

        AdminModuleResponse response = AdminModuleResponse.builder()
                .id(saved.getId())
                .moduleName(saved.getModuleName())
                .moduleCode(saved.getModuleCode())
                .lecturerId(lecturer.getId())
                .lecturerName(lecturer.getFullName())
                .filePath(saved.getFilePath())
                .moduleType(category.getCategoryType())
                .files(saved.getFiles() != null ? saved.getFiles().stream()
                        .map(f -> com.example.learnerassignments.dto.ModuleFileDto.builder()
                                .id(f.getId())
                                .title(f.getTitle())
                                .filePath(f.getFilePath())
                                .originalFilename(f.getOriginalFilename())
                                .fileType(f.getFileType())
                                .build())
                        .collect(Collectors.toList()) : java.util.Collections.emptyList())
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/modules/{id}")
    public ResponseEntity<?> deleteModule(@PathVariable Long id, Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Module not found"));

        if (module.getCategory() == null || module.getCategory().getLecturer() == null ||
            !module.getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own this module");
        }

        moduleRepository.delete(module);
        auditLogService.log(auth, "DELETE_MODULE", "Module", id,
                "Module '" + module.getModuleName() + "' deleted by lecturer " + lecturer.getUsername());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<?> deleteModuleFile(@PathVariable Long id, Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        ModuleFile file = moduleFileRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("File not found"));

        Module module = file.getModule();
        if (module == null || module.getCategory() == null || module.getCategory().getLecturer() == null ||
            !module.getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own the module this file belongs to");
        }

        // Delete physical file on disk if it exists
        if (file.getFilePath() != null) {
            try {
                String relativePath = file.getFilePath().startsWith("/") ? file.getFilePath().substring(1) : file.getFilePath();
                Path path = Paths.get(relativePath);
                Files.deleteIfExists(path);
            } catch (Exception e) {
                // Log and continue, do not block database delete if file delete fails
            }
        }

        moduleFileRepository.delete(file);
        auditLogService.log(auth, "DELETE_MODULE_FILE", "ModuleFile", id,
                "File '" + file.getOriginalFilename() + "' deleted by lecturer " + lecturer.getUsername());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/sessions/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteSession(@PathVariable Long id, Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        SubmissionSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new com.example.learnerassignments.exception.ResourceNotFoundException("Session not found"));

        // Verify session ownership
        if (session.getAssignment() == null || session.getAssignment().getModule() == null ||
            session.getAssignment().getModule().getCategory() == null ||
            session.getAssignment().getModule().getCategory().getLecturer() == null ||
            !session.getAssignment().getModule().getCategory().getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own this session");
        }

        // Soft-delete: mark the session and its submissions as deleted instead of removing
        // the rows, so they can be restored if this was triggered by mistake.
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        submissionRepository.softDeleteBySessionId(id, now);
        session.setDeletedAt(now);
        sessionRepository.save(session);

        auditLogService.log(auth, "DELETE_SESSION", "SubmissionSession", id,
                "Session '" + session.getSessionName() + "' and its submissions soft-deleted by lecturer " + lecturer.getUsername());

        return ResponseEntity.ok().build();
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody java.util.Map<String, String> payload, Authentication auth) {
        Lecturer lecturer = getAuthenticatedLecturer(auth);
        
        String fullName = payload.get("fullName");
        String email = payload.get("email");
        String password = payload.get("password");

        if (fullName != null && !fullName.isBlank()) {
            lecturer.setFullName(fullName);
        }
        if (email != null) {
            lecturer.setEmail(email);
        }
        if (password != null && !password.isBlank()) {
            lecturer.setPasswordHash(passwordEncoder.encode(password));
        }

        Lecturer saved = lecturerRepository.save(lecturer);
        return ResponseEntity.ok(saved);
    }
}
