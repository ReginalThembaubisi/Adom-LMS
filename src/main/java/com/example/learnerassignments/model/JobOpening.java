package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A job or internship the admin advertises on the website's Careers page. Works like a
 * learnership advert: Draft while it is being written, Open while it takes applications (until
 * its closing date), Closed afterwards. Applications to it arrive in the same Applications tab
 * as everything else, linked back to it.
 */
@Entity
@Table(name = "job_openings", indexes = {
        @Index(name = "idx_job_opening_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobOpening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private OpeningCategory category;

    /** e.g. "Software Development", "Technical Support". */
    @Column(name = "division", length = 100)
    private String division;

    @Column(name = "location", length = 255)
    private String location;

    /** How many people will be taken on. */
    @Column(name = "positions")
    private Integer positions;

    /** Last day applications are accepted, inclusive. Null means open until filled. */
    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** One requirement per line. */
    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    /** Same Draft / Open / Closed lifecycle as a learnership advert. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LearnershipStatus status = LearnershipStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isAcceptingApplications(LocalDate today) {
        return status == LearnershipStatus.OPEN && (closingDate == null || !closingDate.isBefore(today));
    }
}
