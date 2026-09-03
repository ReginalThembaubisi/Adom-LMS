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
