package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.GradingHistoryEntryDto;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.security.CurrentStaff;
import com.example.learnerassignments.security.LearnerPrincipal;
import com.example.learnerassignments.service.ScopeService;
import com.example.learnerassignments.service.StoredFileService;
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
    private final CurrentStaff currentStaff;
    private final ScopeService scopeService;
    private final StoredFileService storedFileService;

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

        // 2. Staff, through ScopeService — admins unrestricted, lecturers by the module they
        //    own, assessors and moderators by their assignment rows. An assessor or moderator
        //    holding no rows reaches nothing here, which is the state of every such account
        //    before an admin assigns anyone to it.
        return currentStaff.resolve(auth)
                .map(principal -> scopeService.canAccessSubmission(principal, submission))
                .orElse(false);
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

        // Fetch and re-serve, never redirect. Three reasons, all of which still hold now that
        // new files are stored as authenticated resources: Cloudinary's raw delivery doesn't
        // set an inline-renderable Content-Type, so a redirected viewer renders blank; a
        // redirect to a signed URL hands the browser a working credential for the file; and a
        // redirect discards the ownership check that requireReadable just performed, because
        // the second request never reaches this application at all.
        //
        // StoredFileService works out whether pathStr is a legacy public URL, a public_id, or
        // a path on disk. All three still open — that is the point of the transition.
        Object body = storedFileService.open(pathStr, "submission");

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

        // Writing marks onto a submission is scoped exactly as reading it is: a grader who
        // cannot see a record must not be able to annotate it either.
        Submission submission = submissionService.getSubmission(id);
        boolean graderAuth = currentStaff.resolve(auth)
                .map(principal -> scopeService.canAccessSubmission(principal, submission))
                .orElse(false);
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
