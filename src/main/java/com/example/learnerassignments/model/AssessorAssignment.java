package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One grant of access from an assessor to some part of the cohort.
 *
 * Either a single learner or a whole category, never both — a row names one or the other.
 * An assessor with no rows reaches nothing at all; that is the point, and it is the state
 * every account is in between being created and being assigned.
 */
@Entity
@Table(
        name = "assessor_assignments",
        indexes = {
            @Index(name = "idx_assessor_assignment_assessor", columnList = "assessor_id"),
            @Index(name = "idx_assessor_assignment_learner", columnList = "learner_id"),
            @Index(name = "idx_assessor_assignment_category", columnList = "category_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"assessor", "learner", "category"})
@ToString(exclude = {"assessor", "learner", "category"})
public class AssessorAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessor_id", nullable = false)
    private Assessor assessor;

    /** The single learner this row grants, or null when the row grants a category. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id")
    private Learner learner;

    /** The category this row grants, or null when the row grants one learner. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    void onCreate() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }

    /** A row that names neither grants nothing, and must never be treated as a wildcard. */
    public boolean isEmptyGrant() {
        return learner == null && category == null;
    }
}
