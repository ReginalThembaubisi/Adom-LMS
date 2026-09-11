package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One admin's attempt at importing a zip of historical PoE folders.
 *
 * A row here exists the moment a zip is previewed, before anything is confirmed — the preview
 * itself has to survive an app restart on this free-tier instance while an admin reviews it, and
 * "what did I upload and when" is worth keeping even for a batch that gets cancelled. What must
 * never happen on a cancelled or merely-previewed batch is a write to any learner's document
 * vault; that is enforced in {@code LegacyImportService.confirm}, which is the only place a
 * {@link LearnerDocument} is ever created from this feature, and only for {@link
 * LegacyImportStatus#CONFIRMED} batches.
 */
@Entity
@Table(name = "legacy_import_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegacyImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_username", nullable = false, length = 100)
    private String adminUsername;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /** Where the uploaded zip sits on disk between preview and confirm. Deleted once the batch
     *  leaves PREVIEWED — there is nothing left to import once it has been confirmed, and
     *  nothing worth keeping once it has been cancelled. */
    @Column(name = "stored_zip_path", length = 500)
    private String storedZipPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LegacyImportStatus status;

    @Column(name = "entry_count", nullable = false)
    @Builder.Default
    private int entryCount = 0;

    @Column(name = "importable_count", nullable = false)
    @Builder.Default
    private int importableCount = 0;

    @Column(name = "unmatched_count", nullable = false)
    @Builder.Default
    private int unmatchedCount = 0;

    @Column(name = "imported_count", nullable = false)
    @Builder.Default
    private int importedCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
