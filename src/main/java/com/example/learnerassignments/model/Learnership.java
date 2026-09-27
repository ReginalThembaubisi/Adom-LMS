package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "learnerships")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"categories", "learners"})
@ToString(exclude = {"categories", "learners"})
public class Learnership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "qualification_code")
    private String qualificationCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // --- The public advert. Everything below is what the website shows a prospective
    // applicant; none of it affects how an enrolled learner uses the LMS. ---

    /** URL-safe name the website links to, e.g. "it-systems-support-nqf4". */
    @Column(name = "slug", length = 160, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LearnershipStatus status = LearnershipStatus.DRAFT;

    @Column(name = "seta", length = 100)
    private String seta;

    @Column(name = "nqf_level")
    private Integer nqfLevel;

    @Column(name = "duration_months")
    private Integer durationMonths;

    /** Monthly stipend in whole rand. */
    @Column(name = "stipend")
    private Integer stipend;

    @Column(name = "location", length = 255)
    private String location;

    /** Free text shown on the advert, e.g. "February 2027". */
    @Column(name = "intake", length = 100)
    private String intake;

    /** Last day applications are accepted, inclusive. Null means open until filled. */
    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "max_learners")
    private Integer maxLearners;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** One requirement per line. */
    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "learnership", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Category> categories;

    @OneToMany(mappedBy = "learnership", fetch = FetchType.LAZY)
    private List<Learner> learners;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = LearnershipStatus.DRAFT;
        }
    }

    /**
     * Whether the public can apply today: the advert is Open and its closing date, if it has
     * one, has not passed. The closing date is inclusive — an advert that closes today still
     * takes applications until midnight.
     */
    public boolean isAcceptingApplications(LocalDate today) {
        return status == LearnershipStatus.OPEN && (closingDate == null || !closingDate.isBefore(today));
    }
}
