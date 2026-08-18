package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assessor")
@RequiredArgsConstructor
public class AssessorController {

    private final AssessorRepository assessorRepository;
    private final ModuleRepository moduleRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final SubmissionService submissionService;

    private Assessor getAuthenticatedAssessor(Authentication auth) {
        String username = auth.getName();
        return assessorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated assessor not found"));
    }

    @GetMapping("/modules")
    public ResponseEntity<List<AdminModuleResponse>> getModules(Authentication auth) {
        getAuthenticatedAssessor(auth);
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
        getAuthenticatedAssessor(auth);
        SessionSubmissionOverviewResponse overview = submissionService.getSessionSubmissionsOverview(id);
        return ResponseEntity.ok(overview);
    }

    @PutMapping("/submissions/{id}/grade")
    public ResponseEntity<SubmissionResponse> gradeSubmission(
            @PathVariable Long id,
            @Valid @RequestBody GradeSubmissionRequest request,
            Authentication auth) {

        Assessor assessor = getAuthenticatedAssessor(auth);
        SubmissionResponse response = submissionService.gradeSubmission(id, request, "ASSESSOR", assessor.getFullName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getSessions(Authentication auth) {
        getAuthenticatedAssessor(auth);
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
