package com.example.learnerassignments.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions", indexes = {
    @Index(name = "idx_submission_learner_session", columnList = "learner_id, session_id")
})
@SQLRestriction("deleted_at IS NULL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Learner is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @NotNull(message = "Submission session is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SubmissionSession session;

    @NotBlank(message = "File path is required")
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @NotBlank(message = "Original filename is required")
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    // Which role (FACILITATOR/ASSESSOR/MODERATOR) recorded the current outcome, since all
    // three can grade independently — lets the UI color-code and attribute who marked it.
    @Column(name = "graded_by_role", length = 20)
    private String gradedByRole;

    @Column(name = "graded_by_name")
    private String gradedByName;

    // Numeric mark alongside the Competent/Not Yet Competent outcome — e.g. 85 for 85%.
    @Column(name = "marks_awarded")
    private Integer marksAwarded;

    // A flattened copy of the original document with the grader's pen/tick annotations baked
    // in as images, generated client-side and uploaded once grading is saved. Separate from
    // filePath (the learner's original, untouched submission) so the original is never lost.
    // Legacy path — new submissions store strokes in annotationsJson instead.
    @Column(name = "marked_file_path", length = 500)
    private String markedFilePath;

    // Vector stroke data saved by the in-app annotator: JSON object keyed by page number,
    // each value an array of stroke descriptors ({tool, color, thickness, points}).
    // Stored instead of a rasterized PDF so the original file is never re-encoded and the
    // marked view loads by replaying strokes over the original via pdf.js client-side.
    @Column(name = "annotations_json", columnDefinition = "TEXT")
    private String annotationsJson;

    // --- Portfolio of Evidence columns (Phase 2) ---
    // Added and backfilled only. Nothing reads them yet; the phases that do are named below.

    /**
     * The ModuleFile version of the brief this learner actually worked from.
     *
     * A plain id rather than a relationship: the brief specifies a nullable bigint, and a
     * foreign key here would constrain existing rows that have no guide to point at. Phase 8
     * resolves it so a moderator sees the brief as it was, not as it has since been reissued.
     */
    @Column(name = "guide_version_id")
    private Long guideVersionId;

    /** Whether marking has been released to the learner. Enforced from Phase 5. */
    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_status", length = 20)
    @Builder.Default
    private FeedbackStatus feedbackStatus = FeedbackStatus.DRAFT;

    /** Who the feedback is for. INTERNAL exports but never reaches the portal, from Phase 5. */
    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_visibility", length = 20)
    @Builder.Default
    private FeedbackVisibility feedbackVisibility = FeedbackVisibility.LEARNER;

    /**
     * When the feedback was released to the learner, or null while it is still a draft.
     *
     * Nullable, like every column added to this table after it had rows in it: a NOT NULL
     * column with no default fails the boot that ships it.
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * Whether this submission's marking is the learner's to see.
     *
     * One rule, asked in every learner-facing path, because the ways a learner can reach
     * marking are several — the history list, the feedback list, the marked copy, the
     * annotations — and a rule written out four times is a rule that will be wrong in one of
     * them. Draft marking is not theirs yet; an internal report never will be.
     */
    public boolean isMarkingVisibleToLearner() {
        return feedbackStatus == FeedbackStatus.PUBLISHED
                && feedbackVisibility != FeedbackVisibility.INTERNAL
                && gradedAt != null;
    }

    /**
     * What the learner should see as the state of this submission while marking is withheld:
     * what it was before anyone marked it. Derived rather than remembered, because grading
     * overwrites status and the original is not kept anywhere.
     */
    public SubmissionStatus statusBeforeMarking() {
        LocalDateTime due = session != null && session.getAssignment() != null
                ? session.getAssignment().getDueDate()
                : null;
        return due != null && submittedAt != null && submittedAt.isAfter(due)
                ? SubmissionStatus.LATE
                : SubmissionStatus.SUBMITTED;
    }

    /** Content hash of the submitted file, computed at upload. */
    @Column(name = "sha256", length = 64)
    private String sha256;

    /** Content hash of the marked copy, computed at upload. */
    @Column(name = "marked_sha256", length = 64)
    private String markedSha256;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = SubmissionStatus.SUBMITTED;
        }
    }
}
