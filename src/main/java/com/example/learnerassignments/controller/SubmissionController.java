package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.SubmissionResponse;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.ModeratorRepository;
import com.example.learnerassignments.repository.AssessorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final LecturerRepository lecturerRepository;
    private final AdminRepository adminRepository;
    private final ModeratorRepository moderatorRepository;
    private final AssessorRepository assessorRepository;
    private final PasswordEncoder passwordEncoder;

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

    private boolean checkTokenAccess(Submission submission, String authToken) {
        if (authToken == null || authToken.isBlank()) {
            return false;
        }
        try {
            String base64Credentials = authToken;
            if (base64Credentials.startsWith("Basic ")) {
                base64Credentials = base64Credentials.substring("Basic ".length());
            }
            byte[] decoded = java.util.Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);
            if (parts.length < 2) return false;
            String username = parts[0];
            String password = parts[1];

            // 1. Check if admin
            var adminOpt = adminRepository.findByUsername(username);
            if (adminOpt.isPresent() && passwordEncoder.matches(password, adminOpt.get().getPasswordHash())) {
                return true;
            }

            // 2. Check if lecturer
            var lecturerOpt = lecturerRepository.findByUsername(username);
            if (lecturerOpt.isPresent() && passwordEncoder.matches(password, lecturerOpt.get().getPasswordHash())) {
                Lecturer lecturer = lecturerOpt.get();
                if (submission.getSession() != null &&
                    submission.getSession().getAssignment() != null &&
                    submission.getSession().getAssignment().getModule() != null &&
                    submission.getSession().getAssignment().getModule().getCategory() != null &&
                    submission.getSession().getAssignment().getModule().getCategory().getLecturer() != null) {
                    
                    String assignedUsername = submission.getSession().getAssignment().getModule().getCategory().getLecturer().getUsername();
                    if (username.equals(assignedUsername)) {
                        return true;
                    }
                }
            }

            // 3. Check if moderator
            var modOpt = moderatorRepository.findByUsername(username);
            if (modOpt.isPresent() && passwordEncoder.matches(password, modOpt.get().getPasswordHash())) {
                return true;
            }

            // 4. Check if assessor
            var assOpt = assessorRepository.findByUsername(username);
            if (assOpt.isPresent() && passwordEncoder.matches(password, assOpt.get().getPasswordHash())) {
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadSubmissionFile(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            @RequestParam(value = "authToken", required = false) String authToken,
            Authentication auth) {
        
        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, learnerCode, auth) && !checkTokenAccess(submission, authToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String pathStr = submission.getFilePath();
        if (pathStr != null && (pathStr.startsWith("http://") || pathStr.startsWith("https://"))) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, pathStr)
                    .build();
        }

        Resource resource = submissionService.loadLocalResource(pathStr);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + submission.getOriginalFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/graded-download")
    public ResponseEntity<?> downloadGradedSubmissionFile(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            @RequestParam(value = "authToken", required = false) String authToken,
            Authentication auth) throws IOException {
        
        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, learnerCode, auth) && !checkTokenAccess(submission, authToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (submission.getGradedFilePath() == null || submission.getGradedFilePath().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String pathStr = submission.getGradedFilePath();
        if (pathStr.startsWith("http://") || pathStr.startsWith("https://")) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, pathStr)
                    .build();
        }

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

    @GetMapping("/{id}/download-url")
    public ResponseEntity<?> getDownloadUrl(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            @RequestParam(value = "authToken", required = false) String authToken,
            Authentication auth) {
        
        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, learnerCode, auth) && !checkTokenAccess(submission, authToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String pathStr = submission.getFilePath();
        String downloadUrl;
        if (pathStr != null && (pathStr.startsWith("http://") || pathStr.startsWith("https://"))) {
            downloadUrl = pathStr;
        } else {
            downloadUrl = "/api/submissions/" + id + "/download?stream=true" + (authToken != null ? "&authToken=" + java.net.URLEncoder.encode(authToken, java.nio.charset.StandardCharsets.UTF_8) : "");
        }

        return ResponseEntity.ok(java.util.Map.of("url", downloadUrl));
    }

    @GetMapping("/{id}/graded-download-url")
    public ResponseEntity<?> getGradedDownloadUrl(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            @RequestParam(value = "authToken", required = false) String authToken,
            Authentication auth) {
        
        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, learnerCode, auth) && !checkTokenAccess(submission, authToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (submission.getGradedFilePath() == null || submission.getGradedFilePath().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String pathStr = submission.getGradedFilePath();
        String downloadUrl;
        if (pathStr.startsWith("http://") || pathStr.startsWith("https://")) {
            downloadUrl = pathStr;
        } else {
            downloadUrl = "/api/submissions/" + id + "/graded-download?stream=true" + (authToken != null ? "&authToken=" + java.net.URLEncoder.encode(authToken, java.nio.charset.StandardCharsets.UTF_8) : "");
        }

        return ResponseEntity.ok(java.util.Map.of("url", downloadUrl));
    }
}
