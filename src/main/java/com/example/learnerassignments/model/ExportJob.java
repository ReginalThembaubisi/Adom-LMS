package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One request to build a SETA export bundle, and where it got to.
 *
 * A row exists the moment the request is validated, before any file has been fetched, so the
 * requester has something to poll straight away and the job survives the request thread that
 * created it — the fetch-and-zip work runs on an {@code @Async} worker afterwards.
 *
 * {@code scopeRef} holds a small JSON object describing the scope in enough detail to rebuild
 * it later (which learnership, which cohort, which learner, which staff member's own reach) —
 * a single opaque column rather than one column per possible parameter, because the parameters
 * differ by {@link ExportScopeType} and most rows would otherwise carry columns that mean
 * nothing for that row's type.
 */
@Entity
@Table(
        name = "export_jobs",
        indexes = {
            @Index(name = "idx_export_job_requester", columnList = "requested_by_role, requested_by_id"),
            @Index(name = "idx_export_job_status", columnList = "status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;

    /** ADMIN, ASSESSOR or MODERATOR — whoever may call {@code POST /api/poe/export}. */
    @Column(name = "requested_by_role", nullable = false, length = 20)
    private String requestedByRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private ExportScopeType scopeType;

    /** JSON. See the class note — what it holds depends on {@link #scopeType}. */
    @Column(name = "scope_ref", nullable = false, length = 500)
    private String scopeRef;

    /** A human-readable label for the scope, computed once at creation so the UI never has to
     *  re-resolve names (a learnership renamed later must not change what old jobs say they were). */
    @Column(name = "scope_label", length = 300)
    private String scopeLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ExportJobStatus status = ExportJobStatus.QUEUED;

    private Integer learnerCount;

    private Integer fileCount;

    /**
     * Where the finished zip lives — a Cloudinary public_id when Cloudinary is configured, a
     * local disk path otherwise. Read back exactly like every other stored file in this system,
     * through {@link com.example.learnerassignments.service.StoredFileService}, so the download
     * endpoint needs no export-specific storage logic.
     */
    @Column(name = "result_public_id", length = 500)
    private String resultPublicId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    /**
     * A safe-to-show description of a failure. Never built from a raw exception message: a
     * signed Cloudinary URL can appear in an underlying HTTP client error, and a credential
     * that leaks into a column an admin dashboard renders back to a browser is exactly the
     * leak {@link com.example.learnerassignments.service.StoredFileService} was written to
     * avoid everywhere else.
     */
    @Column(length = 500)
    private String error;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ExportJobStatus.QUEUED;
        }
    }
}
