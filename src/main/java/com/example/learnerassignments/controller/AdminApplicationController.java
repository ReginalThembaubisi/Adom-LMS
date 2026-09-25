package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.ApplicationDtos.*;
import com.example.learnerassignments.model.ApplicationDocument;
import com.example.learnerassignments.service.ApplicationService;
import com.example.learnerassignments.service.AuditLogService;
import com.example.learnerassignments.service.LearnerDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * The admissions pipeline: every application from the website, moved through selection and,
 * for accepted applicants, enrolled on the LMS. Admin-only through SecurityConfig's
 * /api/admin/** rule.
 */
@RestController
@RequestMapping("/api/admin/applications")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final ApplicationService applicationService;
    private final LearnerDocumentService learnerDocumentService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<ApplicationSummary> list(@RequestParam(required = false) Long learnershipId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String q) {
        return applicationService.list(learnershipId, status, q);
    }

    @GetMapping("/{id}")
    public ApplicationDetail detail(@PathVariable Long id) {
        return applicationService.detail(id);
    }

    @PostMapping("/{id}/status")
    public ApplicationSummary changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                           Authentication auth) {
        ApplicationSummary summary = applicationService.changeStatus(id, request, username(auth));
        auditLogService.log(auth, "CHANGE_APPLICATION_STATUS", "LearnershipApplication", id, summary.getStatus());
        return summary;
    }

    @PostMapping("/bulk-status")
    public BulkStatusResponse changeStatusBulk(@Valid @RequestBody BulkStatusRequest request, Authentication auth) {
        BulkStatusResponse response = applicationService.changeStatusBulk(request, username(auth));
        auditLogService.log(auth, "BULK_CHANGE_APPLICATION_STATUS", "LearnershipApplication", null,
                request.getStatus() + " x" + response.getUpdated());
        return response;
    }

    @PutMapping("/{id}/notes")
    public ResponseEntity<Void> updateNotes(@PathVariable Long id, @Valid @RequestBody NotesRequest request) {
        applicationService.updateNotes(id, request.getStaffNotes());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enrol")
    public EnrolResponse enrol(@PathVariable Long id, @Valid @RequestBody(required = false) EnrolRequest request,
                               Authentication auth) {
        EnrolResponse response = applicationService.enrol(id, request, username(auth));
        auditLogService.log(auth, "ENROL_APPLICANT", "LearnershipApplication", id,
                "Learner " + response.getLearnerCode());
        return response;
    }

    /** An applicant's document, streamed through the app like every other personal file. */
    @GetMapping("/{id}/documents/{documentId}/view")
    public ResponseEntity<?> viewDocument(@PathVariable Long id, @PathVariable Long documentId) {
        ApplicationDocument document = applicationService.requireDocument(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        learnerDocumentService.resolveContentType(document.getOriginalFilename())))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getOriginalFilename().replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .body(applicationService.openDocument(document));
    }

    @GetMapping(value = "/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Long learnershipId,
                                            @RequestParam(required = false) String status,
                                            Authentication auth) {
        byte[] body = applicationService.exportCsv(learnershipId, status).getBytes(StandardCharsets.UTF_8);
        auditLogService.log(auth, "EXPORT_APPLICATIONS", "LearnershipApplication", learnershipId,
                status == null ? "all statuses" : status);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"applications-" + LocalDate.now() + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .body(body);
    }

    private static String username(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }
}
