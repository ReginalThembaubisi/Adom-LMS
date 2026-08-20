package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Append-only log of every grading action on a submission. Submission itself only ever holds
// the current/official outcome (what the student sees) — this table preserves what each role
// recorded, in order, so a later grader (e.g. a moderator reviewing a facilitator's mark) can
// see what came before instead of silently overwriting it with no trace.
@Entity
@Table(name = "submission_grading_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionGradingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private SubmissionStatus outcome;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "marks_awarded")
    private Integer marksAwarded;

    @Column(name = "graded_by_role", nullable = false, length = 20)
    private String gradedByRole;

    @Column(name = "graded_by_name", nullable = false)
    private String gradedByName;

    @Column(name = "graded_at", nullable = false)
    private LocalDateTime gradedAt;

    @PrePersist
    protected void onCreate() {
        if (this.gradedAt == null) {
            this.gradedAt = LocalDateTime.now();
        }
    }
}
