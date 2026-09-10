package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** What the portal and the admin screens see of a learner's own documents. */
public class LearnerDocumentDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentResponse {
        private Long id;
        private String documentType;
        private String documentLabel;
        private int poeSection;
        private boolean required;
        private String originalFilename;
        private Integer version;
        private boolean current;
        private String status;
        /** Why it was rejected, shown to the learner as the reviewer wrote it. */
        private String reviewNote;
        private LocalDateTime reviewedAt;
        private LocalDateTime uploadedAt;
        private String uploadedByRole;
    }

    /**
     * One required document and where the learner has got to with it.
     *
     * The portal shows a slot per required type whether or not anything has been supplied, so
     * that a missing document is visible as an empty slot rather than as an absence.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentSlot {
        private String documentType;
        private String documentLabel;
        private int poeSection;
        private boolean required;
        /** The version that counts, or null when nothing has been supplied yet. */
        private DocumentResponse current;
        /** Superseded versions, newest first. */
        private List<DocumentResponse> history;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MyDocumentsResponse {
        private List<DocumentSlot> slots;
        private List<DocumentResponse> other;
        private int requiredTotal;
        private int requiredAccepted;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReviewDocumentRequest {
        /** ACCEPTED or REJECTED. */
        private String status;
        private String note;
    }
}
