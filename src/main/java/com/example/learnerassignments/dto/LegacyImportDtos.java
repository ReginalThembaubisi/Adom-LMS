package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** What the legacy folder importer's preview/confirm/cancel screen sends and reads back. */
public class LegacyImportDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EntryResponse {
        private Long id;
        private String zipEntryPath;
        private String topFolder;
        private Long matchedLearnerId;
        private String matchedLearnerName;
        private String matchConfidence;
        private String documentType;
        private String documentLabel;
        private boolean importable;
        private String note;
        private Long fileSizeBytes;
        private Long importedDocumentId;
        private String importError;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BatchResponse {
        private Long id;
        private String originalFilename;
        private String status;
        private int entryCount;
        private int importableCount;
        private int unmatchedCount;
        private int importedCount;
        private int failedCount;
        private LocalDateTime createdAt;
        private LocalDateTime confirmedAt;
        private LocalDateTime cancelledAt;
        private List<EntryResponse> entries;
    }
}
