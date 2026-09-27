package com.example.learnerassignments.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** What the website's application form sends, and what staff and applicants read back. */
public class ApplicationDtos {

    /**
     * The application form. Bound from multipart form fields (the files travel alongside it,
     * see PublicApplicationController), so a plain HTML form can post it.
     *
     * {@code programmeType} says what they are applying for (COURSE, LEARNERSHIP, INTERNSHIP,
     * JOB or PLACEMENT). When it is left out, naming a learnership implies LEARNERSHIP. Each
     * type needs its own fields: a learnership ({@code learnershipId} or
     * {@code learnershipSlug}), course choices, a position title, or the university and
     * qualification for a placement.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubmitRequest {
        @Size(max = 20) private String programmeType;
        private Long learnershipId;
        @Size(max = 160) private String learnershipSlug;
        /** COURSE: up to three course names, in order of preference. */
        private List<@Size(max = 150) String> courseChoices;
        /** INTERNSHIP or JOB. */
        @Size(max = 200) private String positionTitle;
        @Size(max = 50) private String experience;
        /** PLACEMENT. */
        @Size(max = 150) private String university;
        @Size(max = 150) private String qualification;
        @Size(max = 20) private String placementStart;
        @Size(max = 30) private String placementLength;

        @NotBlank(message = "Choose an ID type") @Size(max = 30) private String idType;
        @NotBlank(message = "ID or passport number is required") @Size(max = 30) private String idNumber;
        @Size(max = 10) private String title;
        @NotBlank(message = "First names are required") @Size(max = 150) private String firstNames;
        @NotBlank(message = "Surname is required") @Size(max = 150) private String surname;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dateOfBirth;
        @Size(max = 30) private String gender;
        @Size(max = 30) private String race;
        private Boolean disability;
        @Size(max = 255) private String disabilityDetails;
        @Size(max = 50) private String homeLanguage;

        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 150)
        private String email;
        @NotBlank(message = "Cellphone number is required") @Size(max = 30) private String phone;
        @Size(max = 255) private String streetAddress;
        @Size(max = 100) private String suburb;
        @Size(max = 100) private String town;
        @Size(max = 10) private String postalCode;
        @Size(max = 50) private String province;

        @Size(max = 150) private String kinName;
        @Size(max = 50) private String kinRelationship;
        @Size(max = 30) private String kinPhone;

        @Size(max = 30) private String highestGrade;
        @Size(max = 150) private String schoolName;
        @Min(1950) @Max(2100) private Integer matricYear;
        @Size(max = 5000) private String subjectsJson;
        @Size(max = 50) private String currentActivity;
        @Size(max = 255) private String previousStudy;
        @Size(max = 5000) private String motivation;

        /** Must be true: the applicant accepted the POPIA notice. */
        private Boolean popiaConsent;

        /**
         * Honeypot. The website renders this field hidden from people; a bot that fills in
         * every field it finds fills this one too, and its submission is quietly dropped.
         */
        private String website;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubmitResponse {
        private String reference;
        private String programmeType;
        /** e.g. "Learnership: IT Systems Support". */
        private String appliedFor;
        /** Set for learnership applications. */
        private String learnershipName;
        private String status;
        private String statusLabel;
        private LocalDateTime submittedAt;
    }

    /** Both are required, so knowing someone's ID number alone reveals nothing. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusLookupRequest {
        @NotBlank(message = "Reference number is required") @Size(max = 20) private String reference;
        @NotBlank(message = "ID or passport number is required") @Size(max = 30) private String idNumber;
    }

    /** What an applicant sees. No staff notes, no reasons, no other applicant's data. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusLookupResponse {
        private String reference;
        private String programmeType;
        private String appliedFor;
        /** Set for learnership applications. */
        private String learnershipName;
        private String firstNames;
        private String status;
        private String statusLabel;
        /** A plain-language next step for the applicant. */
        private String message;
        private LocalDateTime submittedAt;
        private LocalDateTime updatedAt;
        private List<TimelineStage> timeline;
    }

    /** One stage on the applicant's progress timeline: DONE, CURRENT, UPCOMING or FAILED. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TimelineStage {
        private String key;
        private String label;
        private String state;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApplicationSummary {
        private Long id;
        private String reference;
        private String fullName;
        private String idNumber;
        private String email;
        private String phone;
        private String programmeType;
        private String programmeLabel;
        /** e.g. "Course: Systems Development; Software Testing". */
        private String appliedFor;
        private Long learnershipId;
        private String learnershipName;
        private String hostCompany;
        private String status;
        private String statusLabel;
        private String town;
        private String province;
        private String highestGrade;
        private String currentActivity;
        private int documentCount;
        private LocalDateTime submittedAt;
        private LocalDateTime updatedAt;
        private String enrolledLearnerCode;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApplicationDetail {
        private ApplicationSummary summary;
        private String idType;
        private String title;
        private String firstNames;
        private String surname;
        private LocalDate dateOfBirth;
        private String gender;
        private String race;
        private Boolean disability;
        private String disabilityDetails;
        private String homeLanguage;
        private String streetAddress;
        private String suburb;
        private String postalCode;
        private String kinName;
        private String kinRelationship;
        private String kinPhone;
        private String schoolName;
        private Integer matricYear;
        private String subjectsJson;
        private String courseChoices;
        private String positionTitle;
        private String experience;
        private String university;
        private String qualification;
        private String placementStart;
        private String placementLength;
        private String previousStudy;
        private String motivation;
        private LocalDateTime popiaConsentAt;
        private String staffNotes;
        private Long enrolledLearnerId;
        private List<DocumentDto> documents;
        private List<EventDto> events;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentDto {
        private Long id;
        private String documentType;
        private String label;
        private String originalFilename;
        private Long sizeBytes;
        private LocalDateTime uploadedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EventDto {
        private String fromStatus;
        private String toStatus;
        private String note;
        private String changedBy;
        private LocalDateTime changedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusChangeRequest {
        @NotBlank(message = "Choose a status") private String status;
        @Size(max = 1000) private String note;
        /** Whether to email the applicant about the change. Defaults to true. */
        private Boolean notifyApplicant;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BulkStatusRequest {
        @NotEmpty(message = "Choose at least one application") private List<Long> ids;
        @NotBlank(message = "Choose a status") private String status;
        @Size(max = 1000) private String note;
        private Boolean notifyApplicant;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BulkStatusResponse {
        private int updated;
        /** Applications left as they were, with the reason, e.g. already enrolled. */
        private List<String> skipped;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class NotesRequest {
        @Size(max = 10000) private String staffNotes;
        /** INTERNSHIP or PLACEMENT: where staff placed the applicant. Null leaves it unchanged. */
        @Size(max = 200) private String hostCompany;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EnrolRequest {
        /** The cohort to place the new learner in. Defaults to the learnership's intake. */
        @Size(max = 100) private String cohort;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EnrolResponse {
        private Long learnerId;
        private String learnerCode;
        private String fullName;
        private String cohort;
        private int documentsCopied;
        private int modulesEnrolled;
    }

    // --- Services appointment requests ---

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AppointmentSubmitRequest {
        @NotBlank(message = "Your name is required") @Size(max = 150) private String fullName;
        @Size(max = 150) private String company;
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 150)
        private String email;
        @NotBlank(message = "Cellphone number is required") @Size(max = 30) private String phone;
        @NotBlank(message = "Choose a service") @Size(max = 60) private String service;
        private LocalDate preferredDate;
        @Size(max = 30) private String preferredTime;
        @Size(max = 5000) private String message;
        /** Honeypot, as on the application form. */
        private String website;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AppointmentDto {
        private Long id;
        private String fullName;
        private String company;
        private String email;
        private String phone;
        private String service;
        private LocalDate preferredDate;
        private String preferredTime;
        private String message;
        private String status;
        private String staffNotes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AppointmentUpdateRequest {
        /** NEW, CONFIRMED, COMPLETED or CANCELLED. Null leaves it unchanged. */
        private String status;
        @Size(max = 10000) private String staffNotes;
    }
}
