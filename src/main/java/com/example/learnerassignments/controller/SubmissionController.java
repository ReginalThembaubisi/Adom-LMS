package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.SubmissionResponse;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> submitAssignment(
            @RequestParam("learner_code") String learnerCode,
            @RequestParam("session_id") Long sessionId,
            @RequestParam("file") MultipartFile file) {

        SubmissionResponse response = submissionService.submitAssignment(learnerCode, sessionId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private boolean checkAccess(Submission submission, String learnerCode, Authentication auth) {
        // 1. Check if learnerCode query param matches
        if (learnerCode != null && !learnerCode.isBlank() &&
            submission.getLearner() != null &&
            learnerCode.equals(submission.getLearner().getLearnerCode())) {
            return true;
        }

        // 2. Check if authenticated lecturer/admin matches
        if (auth != null && auth.isAuthenticated()) {
            if (submission.getSession() != null &&
                submission.getSession().getAssignment() != null &&
                submission.getSession().getAssignment().getModule() != null &&
                submission.getSession().getAssignment().getModule().getCategory() != null &&
                submission.getSession().getAssignment().getModule().getCategory().getLecturer() != null) {
                
                String lecturerUsername = submission.getSession().getAssignment().getModule().getCategory().getLecturer().getUsername();
                if (auth.getName().equals(lecturerUsername)) {
                    return true;
                }
            }
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return true;
            }
        }

        return false;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSubmissionFile(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            Authentication auth) {
        
        Map.Entry<Submission, Resource> entry = submissionService.loadSubmissionResource(id);
        Submission submission = entry.getKey();
        Resource resource = entry.getValue();

        if (!checkAccess(submission, learnerCode, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + submission.getOriginalFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/graded-download")
    public ResponseEntity<Resource> downloadGradedSubmissionFile(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            Authentication auth) throws IOException {
        
        Map.Entry<Submission, Resource> entry = submissionService.loadSubmissionResource(id);
        Submission submission = entry.getKey();

        if (!checkAccess(submission, learnerCode, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (submission.getGradedFilePath() == null || submission.getGradedFilePath().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String pathStr = submission.getGradedFilePath();
        if (pathStr.startsWith("/")) {
            pathStr = pathStr.substring(1);
        }
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();
        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + submission.getGradedOriginalFilename() + "\"")
                .body(resource);
    }
}
