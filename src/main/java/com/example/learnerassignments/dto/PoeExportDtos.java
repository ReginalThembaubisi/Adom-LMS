package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** What the export screen sends and reads back. */
public class PoeExportDtos {

    /**
     * What to export. Which fields matter depends on {@code scopeType}:
     *
     * <ul>
     *   <li>LEARNER — {@code learnerId}</li>
     *   <li>COHORT — {@code learnershipId}, {@code cohort}</li>
     *   <li>LEARNERSHIP — {@code learnershipId}</li>
     *   <li>SECTION — {@code learnershipId}, {@code categoryId}</li>
     *   <li>MODERATION_SAMPLE — nothing; it is always the caller's own reach. An admin
     *       requesting one on behalf of a specific assessor or moderator names them via
     *       {@code staffRole} and {@code staffId}.</li>
     * </ul>
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateExportRequest {
        private String scopeType;
        private Long learnershipId;
        private String cohort;
        private Long learnerId;
        private Long categoryId;
        private String staffRole;
        private Long staffId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExportJobResponse {
        private Long id;
        private String scopeType;
        private String scopeLabel;
        private String status;
        private Integer learnerCount;
        private Integer fileCount;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String error;
        private boolean downloadAvailable;
    }
}
