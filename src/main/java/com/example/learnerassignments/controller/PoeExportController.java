package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.PoeExportDtos.CreateExportRequest;
import com.example.learnerassignments.dto.PoeExportDtos.ExportJobResponse;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.ExportJob;
import com.example.learnerassignments.model.ExportJobStatus;
import com.example.learnerassignments.security.CurrentStaff;
import com.example.learnerassignments.security.StaffPrincipal;
import com.example.learnerassignments.service.PoeExportService;
import com.example.learnerassignments.service.StoredFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The SETA export: request one, watch it, download it.
 *
 * Reachable by ADMIN, ASSESSOR and MODERATOR — {@code SecurityConfig} gates the whole prefix to
 * those three roles, and {@link PoeExportService#createJob} narrows each scope type from there.
 * Not LECTURER: nothing in the brief gives a facilitator a reason to pull a portfolio bundle,
 * and opening it up is easy to do later if that turns out wrong.
 */
@RestController
@RequestMapping("/api/poe/export")
@RequiredArgsConstructor
public class PoeExportController {

    private final PoeExportService exportService;
    private final CurrentStaff currentStaff;
    private final StoredFileService storedFileService;

    @PostMapping
    public ResponseEntity<ExportJobResponse> create(@RequestBody CreateExportRequest request, Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        ExportJob job = exportService.createJob(principal, request);
        exportService.runExportAsync(job.getId());
        return ResponseEntity.accepted().body(exportService.toResponse(job));
    }

    @GetMapping
    public ResponseEntity<List<ExportJobResponse>> list(Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        List<ExportJobResponse> jobs = exportService.listVisibleTo(principal).stream()
                .map(exportService::toResponse)
                .toList();
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ExportJobResponse> get(@PathVariable Long jobId, Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        ExportJob job = exportService.requireVisible(jobId, principal);
        return ResponseEntity.ok(exportService.toResponse(job));
    }

    /**
     * Streams the finished zip back through this server — never a redirect to Cloudinary and
     * never a signed URL handed to the browser, the same rule every other stored file in this
     * system follows. Ownership is checked here (admin, or whoever requested it); nothing about
     * whether that identity could still be assigned the underlying learners is re-checked at
     * download time, matching how a finished backup is not re-validated against currently-live
     * grants either.
     */
    @GetMapping("/{jobId}/download")
    public ResponseEntity<?> download(@PathVariable Long jobId, Authentication auth) {
        StaffPrincipal principal = currentStaff.require(auth);
        ExportJob job = exportService.requireVisible(jobId, principal);
        if (job.getStatus() != ExportJobStatus.COMPLETED || job.getResultPublicId() == null) {
            throw new ResourceNotFoundException("This export is not ready to download.");
        }

        Object content = storedFileService.open(job.getResultPublicId(), "export bundle");
        String filename = "poe_export_" + job.getId() + ".zip";
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        if (content instanceof byte[] bytes) {
            return response.body(bytes);
        }
        return response.body((Resource) content);
    }
}
