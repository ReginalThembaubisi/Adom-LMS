package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One person's application, submitted from the public website: for a course, a learnership,
 * an internship, a job, or a university work placement ({@link ApplicationType}). The table
 * keeps its original name from when learnerships were the only kind.
 *
 * Deliberately separate from {@link Learner}: most applicants are never enrolled, and an
 * applicant has no password, no student number and no access to anything on the LMS. The
 * enrol action is the only bridge — it creates the learner from this record and carries the
 * documents across, so nobody types the same details or uploads the same ID copy twice.
 */
@Entity
@Table(name = "learnership_applications", indexes = {
        @Index(name = "idx_application_learnership_status", columnList = "learnership_id, status"),
        @Index(name = "idx_application_type_status", columnList = "programme_type, status"),
        @Index(name = "idx_application_opening", columnList = "opening_id"),
        @Index(name = "idx_application_id_number", columnList = "id_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"learnership", "opening", "documents", "events", "enrolledLearner"})
@ToString(exclude = {"learnership", "opening", "documents", "events", "enrolledLearner"})
public class LearnershipApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What the applicant quotes when they check their status, e.g. ADM-7KQ2M9XP. */
    @Column(name = "reference", nullable = false, unique = true, length = 20)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "programme_type", nullable = false, length = 20)
    @Builder.Default
    private ApplicationType programmeType = ApplicationType.LEARNERSHIP;

    /** Set for LEARNERSHIP applications only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learnership_id")
    private Learnership learnership;

    /** INTERNSHIP or JOB applications made against an advertised opening. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opening_id")
    private JobOpening opening;

    // --- What they applied for, by type ---

    /** COURSE: up to three course names in order of preference, separated by "; ". */
    @Column(name = "course_choices", length = 500)
    private String courseChoices;

    /** INTERNSHIP or JOB: the opening's title, e.g. "Software Developer". */
    @Column(name = "position_title", length = 200)
    private String positionTitle;

    /** PLACEMENT: the student's university. */
    @Column(name = "university", length = 150)
    private String university;

    /** PLACEMENT: the qualification the workplace period counts towards. */
    @Column(name = "qualification", length = 150)
    private String qualification;

    /** PLACEMENT: when the workplace period must start, e.g. "2027-02". */
    @Column(name = "placement_start", length = 20)
    private String placementStart;

    /** PLACEMENT: how long it must be, e.g. "3 months". */
    @Column(name = "placement_length", length = 30)
    private String placementLength;

    /** INTERNSHIP or JOB: work experience, e.g. "1–3 years". */
    @Column(name = "experience", length = 50)
    private String experience;

    /** INTERNSHIP or PLACEMENT: the company staff placed the applicant with. */
    @Column(name = "host_company", length = 200)
    private String hostCompany;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    // --- Identity ---
    @Column(name = "id_type", nullable = false, length = 30)
    private String idType;

    @Column(name = "id_number", nullable = false, length = 30)
    private String idNumber;

    @Column(name = "title", length = 10)
    private String title;

    @Column(name = "first_names", nullable = false, length = 150)
    private String firstNames;

    @Column(name = "surname", nullable = false, length = 150)
    private String surname;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    // Gender, race and disability are asked because SETAs report on them; they are never used
    // to filter or rank applicants in this system.
    @Column(name = "gender", length = 30)
    private String gender;

    @Column(name = "race", length = 30)
    private String race;

    @Column(name = "disability")
    private Boolean disability;

    @Column(name = "disability_details", length = 255)
    private String disabilityDetails;

    @Column(name = "home_language", length = 50)
    private String homeLanguage;

    // --- Contact ---
    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "street_address", length = 255)
    private String streetAddress;

    @Column(name = "suburb", length = 100)
    private String suburb;

    @Column(name = "town", length = 100)
    private String town;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "province", length = 50)
    private String province;

    // --- Next of kin ---
    @Column(name = "kin_name", length = 150)
    private String kinName;

    @Column(name = "kin_relationship", length = 50)
    private String kinRelationship;

    @Column(name = "kin_phone", length = 30)
    private String kinPhone;

    // --- Education and background ---
    @Column(name = "highest_grade", length = 30)
    private String highestGrade;

    @Column(name = "school_name", length = 150)
    private String schoolName;

    @Column(name = "matric_year")
    private Integer matricYear;

    /** The subjects-and-marks list from the website form, as JSON. Shown to staff as-is. */
    @Column(name = "subjects_json", columnDefinition = "TEXT")
    private String subjectsJson;

    /** At school, unemployed, employed, studying elsewhere... */
    @Column(name = "current_activity", length = 50)
    private String currentActivity;

    @Column(name = "previous_study", length = 255)
    private String previousStudy;

    @Column(name = "motivation", columnDefinition = "TEXT")
    private String motivation;

    // --- Record keeping ---
    /** When the applicant accepted the POPIA notice. An application cannot exist without it. */
    @Column(name = "popia_consent_at", nullable = false)
    private LocalDateTime popiaConsentAt;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Staff-only notes. Never shown to the applicant. */
    @Column(name = "staff_notes", columnDefinition = "TEXT")
    private String staffNotes;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    /** Set once the enrol action has created a learner from this application. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrolled_learner_id")
    private Learner enrolledLearner;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("uploadedAt ASC")
    @Builder.Default
    private List<ApplicationDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("changedAt ASC")
    @Builder.Default
    private List<ApplicationStatusEvent> events = new ArrayList<>();

    public String getFullName() {
        return (firstNames + " " + surname).trim();
    }

    /** What they applied for, for staff lists and exports: "Learnership: IT Systems Support". */
    public String getAppliedFor() {
        String what = switch (programmeType) {
            case LEARNERSHIP -> learnership != null ? learnership.getName() : null;
            case COURSE -> courseChoices;
            case INTERNSHIP, JOB -> positionTitle;
            case PLACEMENT -> qualification != null && university != null ? qualification + " (" + university + ")"
                    : qualification != null ? qualification : university;
        };
        return what == null || what.isBlank() ? programmeType.getLabel() : programmeType.getLabel() + ": " + what;
    }

    /** How emails to the applicant name what they applied for: "the IT Systems Support learnership". */
    public String getAppliedForPhrase() {
        return switch (programmeType) {
            case LEARNERSHIP -> learnership != null ? "the " + learnership.getName() + " learnership" : "a learnership";
            case COURSE -> courseChoices != null && !courseChoices.isBlank()
                    ? "the " + courseChoices.split(";")[0].trim() + " course" : "a course";
            case INTERNSHIP -> positionTitle != null ? "the " + positionTitle + " internship" : "an internship";
            case JOB -> positionTitle != null ? "the " + positionTitle + " position" : "a position";
            case PLACEMENT -> "a university work placement";
        };
    }
}
