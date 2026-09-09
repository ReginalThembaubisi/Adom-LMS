package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One grant of access from a moderator to a learnership, optionally narrowed to a cohort.
 *
 * A moderator with no rows reaches nothing. A row with a null cohort covers the whole
 * learnership; a row naming a cohort covers only that cohort within it.
 */
@Entity
@Table(
        name = "moderator_assignments",
        indexes = {
            @Index(name = "idx_moderator_assignment_moderator", columnList = "moderator_id"),
            @Index(name = "idx_moderator_assignment_learnership", columnList = "learnership_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"moderator", "learnership"})
@ToString(exclude = {"moderator", "learnership"})
public class ModeratorAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private Moderator moderator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learnership_id", nullable = false)
    private Learnership learnership;

    /** Null means every cohort on the learnership. */
    @Column(name = "cohort", length = 100)
    private String cohort;

    /**
     * What the moderator is here to do — FULL for everything on the assignment, SAMPLE for a
     * moderation sample. Recorded so the export and the dashboards can say which it is; it
     * never widens what the rows already grant.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    @Builder.Default
    private ModerationScope scope = ModerationScope.FULL;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    void onCreate() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
        if (scope == null) {
            scope = ModerationScope.FULL;
        }
    }
}
