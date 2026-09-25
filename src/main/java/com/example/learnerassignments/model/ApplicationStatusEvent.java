package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One step in an application's history: who moved it, from what, to what, and why. Never
 * edited or deleted, so the record of how an applicant was treated survives any later change.
 */
@Entity
@Table(name = "application_status_events", indexes = {
        @Index(name = "idx_application_status_events_application", columnList = "application_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "application")
@ToString(exclude = "application")
public class ApplicationStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LearnershipApplication application;

    /** Null for the event that records the application arriving. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ApplicationStatus toStatus;

    /** Staff-only. Not shown to the applicant. */
    @Column(name = "note", length = 1000)
    private String note;

    /** Admin username, or "applicant" for the submission itself. */
    @Column(name = "changed_by", nullable = false, length = 150)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
