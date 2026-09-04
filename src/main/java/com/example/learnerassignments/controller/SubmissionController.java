package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.GradingHistoryEntryDto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    // Serves the submission file for in-app viewing only (iframe/Google Docs Viewer). Uses
    // Content-Disposition: inline so browsers render it instead of prompting a file save —
    // there is deliberately no "attachment" download path left for submissions anymore.
    @GetMapping("/{id}/view")
    public ResponseEntity<?> viewSubmissionFile(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            @RequestParam(value = "authToken", required = false) String authToken,
            @RequestParam(value = "marked", required = false, defaultValue = "false") boolean marked,
            Authentication auth) {

        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, learnerCode, auth) && !checkTokenAccess(submission, authToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // The marked (annotated) copy is always uploaded as a PDF regardless of the original
        // format, since it's flattened from rendered pages — so it's served as one whenever
        // present, rather than falling back to the original's filename-derived content type.
        String pathStr = (marked && submission.getMarkedFilePath() != null)
                ? submission.getMarkedFilePath()
                : submission.getFilePath();
        String contentType = (marked && submission.getMarkedFilePath() != null)
                ? "application/pdf"
                : submissionService.resolveContentType(submission.getOriginalFilename());

        Object body;
        if (pathStr != null && (pathStr.startsWith("http://") || pathStr.startsWith("https://"))) {
            // Fetch and re-serve rather than redirecting: Cloudinary's raw-resource delivery
            // doesn't reliably set an inline-renderable Content-Type on its own, which left
            // the in-app viewer blank for externally-stored files.
            body = submissionService.fetchExternalFile(pathStr);
        } else {
            body = submissionService.loadLocalResource(pathStr);
        }

        String filename = (marked && submission.getMarkedFilePath() != null)
                ? "marked_" + submission.getOriginalFilename()
                : submission.getOriginalFilename();

        // Original files never change after upload; marked rasters are replaced atomically.
        // Both qualify for long-lived private caching keyed by submission id + variant.
        String etag = "\"sub-" + id + "-" + (marked ? "m" : "o") + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, immutable, max-age=31536000")
                .header(HttpHeaders.ETAG, etag)
                .body(body);
    }

    // Stores vector stroke data (JSON) in place of a rasterized marked-copy blob.
    // Only authenticated graders (via token or session) may write; learners may read.
    @PutMapping(value = "/{id}/annotations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> saveAnnotations(
            @PathVariable Long id,
            @RequestParam(value = "authToken", required = false) String authToken,
            @RequestBody String json,
            Authentication auth) {

        Submission submission = submissionService.getSubmission(id);
        boolean graderAuth = checkTokenAccess(submission, authToken) ||
                (auth != null && auth.isAuthenticated() &&
                 auth.getAuthorities().stream().anyMatch(a ->
                         a.getAuthority().equals("ROLE_ADMIN") ||
                         a.getAuthority().equals("ROLE_LECTURER") ||
                         a.getAuthority().equals("ROLE_ASSESSOR") ||
                         a.getAuthority().equals("ROLE_MODERATOR")));
        if (!graderAuth) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        submissionService.saveAnnotations(id, json);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/annotations")
    public ResponseEntity<String> getAnnotations(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            @RequestParam(value = "authToken", required = false) String authToken,
            Authentication auth) {

        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, learnerCode, auth) && !checkTokenAccess(submission, authToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String json = submissionService.getAnnotationsJson(id);
        if (json == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // Every past grading action on this submission, oldest first, so a grader opening a
    // submission someone else already assessed can see what was decided before them instead
    // of unknowingly overwriting it. Shared across all grading roles via the same access
    // checks as the file viewer above.
    @GetMapping("/{id}/grading-history")
    public ResponseEntity<List<GradingHistoryEntryDto>> getGradingHistory(
            @PathVariable Long id,
            @RequestParam(value = "learnerCode", required = false) String learnerCode,
            @RequestParam(value = "authToken", required = false) String authToken,
            Authentication auth) {

        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, learnerCode, auth) && !checkTokenAccess(submission, authToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(submissionService.getGradingHistory(id));
    }
}
