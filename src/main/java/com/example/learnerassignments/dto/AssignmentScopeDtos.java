package com.example.learnerassignments.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Requests and responses for the admin screens that grant assessors and moderators access. */
public class AssignmentScopeDtos {

    /** One grant to an assessor: a learner or a category, never both and never neither. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateAssessorAssignmentRequest {
        private Long learnerId;
        private Long categoryId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AssessorAssignmentResponse {
        private Long id;
        private Long assessorId;
        private String assessorName;
        private Long learnerId;
        private String learnerName;
        private String learnerCode;
        private Long categoryId;
        private String categoryType;
        private LocalDateTime assignedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateModeratorAssignmentRequest {
        @NotNull(message = "A learnership is required")
        private Long learnershipId;
        /** Null covers every cohort on the learnership. */
        private String cohort;
        /** FULL or SAMPLE; defaults to FULL. */
        private String scope;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ModeratorAssignmentResponse {
        private Long id;
        private Long moderatorId;
        private String moderatorName;
        private Long learnershipId;
        private String learnershipName;
        private String cohort;
        private String scope;
        private LocalDateTime assignedAt;
    }
}
