package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One version of one personal document belonging to one learner.
 *
 * Rows are never overwritten. Re-uploading a CV writes a new row and marks the previous one
 * superseded, so what was supplied and when stays on the record — which is what a portfolio
 * being audited by a SETA needs, and what a rejected-then-resubmitted document needs in order
 * to show its own history.
 */
@Entity
@Table(
        name = "learner_documents",
        indexes = {
            @Index(name = "idx_learner_documents_learner", columnList = "learner_id"),
            @Index(name = "idx_learner_documents_current", columnList = "learner_id, document_type, is_current")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "learner")
@ToString(exclude = "learner")
public class LearnerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private PoeDocumentType documentType;

    /**
     * Where the file is. Today that is whatever the upload returns — a Cloudinary URL, or an
     * absolute path when Cloudinary is not configured. Phase 4 changes this to a public_id and
     * has to cope with both shapes; the reader already does.
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /** Hash of the bytes received, so Phase 9 can sign this without fetching it. */
    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * Whether this is the version that counts.
     *
     * NOT NULL, unlike the Phase 2 columns added to tables that already held rows. This table
     * is new, so there is nothing to leave null and no reason to allow it — a null here would
     * mean a document that is neither current nor superseded. The readers still handle null
     * defensively anyway, because a row that vanishes from a filter looks like a lost document
     * rather than a bug.
     */
    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean current = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    /** Why it was rejected, or any note a reviewer left. Shown to the learner as written. */
    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /** LEARNER for anything supplied through the portal. */
    @Column(name = "uploaded_by_role", nullable = false, length = 20)
    private String uploadedByRole;

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (version == null) {
            version = 1;
        }
        if (current == null) {
            current = true;
        }
        if (status == null) {
            status = ReviewStatus.PENDING;
        }
    }
}
