package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.GradingHistoryEntryDto;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.security.LearnerPrincipal;
import com.example.learnerassignments.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    // Learners submit through POST /api/me/submissions. This endpoint used to take the
    // learner code as a form field, which meant anyone could submit as anyone.

    /**
     * Whether this caller may read this submission: the learner it belongs to, or staff.
     *
     * Everyone here is authenticated by the filter chain before the request arrives —
     * learners by bearer token, staff by HTTP Basic. Nothing is read from the query string.
     * There used to be a second path that decoded base64 Basic credentials out of an
     * ?authToken parameter, which put lecturer and admin passwords into access logs, browser
     * history and referrer headers, and — through the Word preview — into Google's request
     * logs. It is gone; the browser contexts that needed it now fetch with a header and
     * render from an object URL.
     */
    private boolean checkAccess(Submission submission, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        // 1. The learner it belongs to. Identity comes from the session; a learner code is
        //    never accepted as an argument here, since codes are neither secret nor proof.
        if (auth.getPrincipal() instanceof LearnerPrincipal principal) {
            return submission.getLearner() != null
                    && submission.getLearner().getId().equals(principal.learnerId());
        }

        // 2. Admins, and the assessor and moderator roles.
        //
        //    Assessors and moderators are unscoped here, which is the access they already had
        //    through the parameter this change removes — the point of this task is where the
        //    credential travels, not who may read what. Narrowing them to their assigned
        //    learners is the next task in this phase, once assessor_assignment and
        //    moderator_assignment exist for ScopeService to resolve against.
        if (hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_ASSESSOR") || hasRole(auth, "ROLE_MODERATOR")) {
            return true;
        }

        // 3. The lecturer who owns the module this submission was made against.
        if (submission.getSession() != null &&
            submission.getSession().getAssignment() != null &&
            submission.getSession().getAssignment().getModule() != null &&
            submission.getSession().getAssignment().getModule().getCategory() != null &&
            submission.getSession().getAssignment().getModule().getCategory().getLecturer() != null) {

            String lecturerUsername = submission.getSession().getAssignment().getModule()
                    .getCategory().getLecturer().getUsername();
            return auth.getName().equals(lecturerUsername);
        }

        return false;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    /**
     * The submission, or 404 if this caller may not read it.
     *
     * Denial is always "not found", never "forbidden": submission ids are sequential, and a
     * 403 on someone else's id confirms it exists — enough to map the whole cohort.
     */
    private Submission requireReadable(Long id, Authentication auth) {
        Submission submission = submissionService.getSubmission(id);
        if (!checkAccess(submission, auth)) {
            throw new ResourceNotFoundException("Submission not found with id: " + id);
        }
        return submission;
    }

    // Serves the submission file for in-app viewing only (iframe/Google Docs Viewer). Uses
    // Content-Disposition: inline so browsers render it instead of prompting a file save —
    // there is deliberately no "attachment" download path left for submissions anymore.
    //
    // Both the portal and the grading workspace read this with fetch() and an Authorization
    // header, rendering the result from an object URL, so no caller needs a credential in the
    // query string and the endpoint requires authentication at the filter.
    @GetMapping("/{id}/view")
    public ResponseEntity<?> viewSubmissionFile(
            @PathVariable Long id,
            @RequestParam(value = "marked", required = false, defaultValue = "false") boolean marked,
            Authentication auth) {

        Submission submission = requireReadable(id, auth);

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
            @RequestBody String json,
            Authentication auth) {

        boolean graderAuth = auth != null && auth.isAuthenticated() &&
                (hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_LECTURER") ||
                 hasRole(auth, "ROLE_ASSESSOR") || hasRole(auth, "ROLE_MODERATOR"));
        if (!graderAuth) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        submissionService.saveAnnotations(id, json);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/annotations")
    public ResponseEntity<String> getAnnotations(@PathVariable Long id, Authentication auth) {
        requireReadable(id, auth);
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
            @PathVariable Long id, Authentication auth) {

        requireReadable(id, auth);
        return ResponseEntity.ok(submissionService.getGradingHistory(id));
    }
}
