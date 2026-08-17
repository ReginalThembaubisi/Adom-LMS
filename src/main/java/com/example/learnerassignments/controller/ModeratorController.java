package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.service.CloudinaryService;
import com.example.learnerassignments.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/moderator")
@RequiredArgsConstructor
public class ModeratorController {

    private final ModeratorRepository moderatorRepository;
    private final ModuleRepository moduleRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionService submissionService;
    private final CloudinaryService cloudinaryService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private Moderator getAuthenticatedModerator(Authentication auth) {
        String username = auth.getName();
        return moderatorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated moderator not found"));
    }

    @GetMapping("/modules")
    public ResponseEntity<List<AdminModuleResponse>> getModules(Authentication auth) {
        getAuthenticatedModerator(auth);
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
                                .map(f -> ModuleFileDto.builder()
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

    @GetMapping("/sessions/{id}/submissions")
    public ResponseEntity<SessionSubmissionOverviewResponse> getSessionSubmissions(
            @PathVariable Long id,
            Authentication auth) {
        getAuthenticatedModerator(auth);
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
        
        getAuthenticatedModerator(auth);
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        submission.setGrade(grade);
        submission.setFeedback(feedback);
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(LocalDateTime.now());

        if (file != null && !file.isEmpty()) {
            String gradedUrl;
            if (cloudinaryService.isConfigured()) {
                gradedUrl = cloudinaryService.uploadFile(file);
            } else {
                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();

                String uniqueFilename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                Path filePath = Paths.get(uploadDir, uniqueFilename);
                Files.copy(file.getInputStream(), filePath);

                gradedUrl = "/" + uploadDir + "/" + uniqueFilename;
            }
            submission.setGradedFilePath(gradedUrl);
            submission.setGradedOriginalFilename(file.getOriginalFilename());
        }

        submissionRepository.save(submission);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getSessions(Authentication auth) {
        getAuthenticatedModerator(auth);
        List<SessionResponse> list = sessionRepository.findAll().stream()
                .filter(ss -> ss.getAssignment() != null && ss.getAssignment().getModule() != null)
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
        return ResponseEntity.ok(list);
    }
}
