package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.LegacyImportDtos.BatchResponse;
import com.example.learnerassignments.model.LegacyImportBatch;
import com.example.learnerassignments.service.LegacyImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Phase 10: importing a zip of historical PoE folders that predate this system.
 *
 * Admin-only, per {@code SecurityConfig}'s {@code /api/admin/**} rule. Every state change here
 * is deliberately explicit — preview, then confirm or cancel — with nothing implicit in between,
 * because {@link #confirm} is the one call in this controller that writes to a learner's
 * document vault at all.
 */
@RestController
@RequestMapping("/api/admin/poe/import")
@RequiredArgsConstructor
public class LegacyImportController {

    private final LegacyImportService importService;

    @PostMapping
    public ResponseEntity<BatchResponse> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "learnerId", required = false) Long learnerId,
            Authentication auth) {
        LegacyImportBatch batch = importService.preview(file, auth.getName(), learnerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(importService.toResponse(batch, importService.entriesFor(batch.getId())));
    }

    @GetMapping
    public ResponseEntity<List<BatchResponse>> list() {
        List<BatchResponse> batches = importService.listBatches().stream()
                .map(b -> importService.toResponse(b, null))
                .toList();
        return ResponseEntity.ok(batches);
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<BatchResponse> get(@PathVariable Long batchId) {
        LegacyImportBatch batch = importService.requireBatch(batchId);
        return ResponseEntity.ok(importService.toResponse(batch, importService.entriesFor(batchId)));
    }

    @PostMapping("/{batchId}/confirm")
    public ResponseEntity<BatchResponse> confirm(@PathVariable Long batchId, Authentication auth) {
        LegacyImportBatch batch = importService.confirm(batchId, auth.getName());
        return ResponseEntity.ok(importService.toResponse(batch, importService.entriesFor(batchId)));
    }

    @PostMapping("/{batchId}/cancel")
    public ResponseEntity<BatchResponse> cancel(@PathVariable Long batchId, Authentication auth) {
        LegacyImportBatch batch = importService.cancel(batchId, auth.getName());
        return ResponseEntity.ok(importService.toResponse(batch, importService.entriesFor(batchId)));
    }
}
