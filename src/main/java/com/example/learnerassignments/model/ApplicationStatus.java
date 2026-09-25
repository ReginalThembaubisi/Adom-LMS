package com.example.learnerassignments.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where an application to a learnership stands, from the moment it arrives from the website
 * to the moment the applicant becomes a learner on the LMS.
 *
 * <pre>
 * SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → ACCEPTED → ENROLLED
 *                                    ↘ WAITLISTED / DECLINED
 * </pre>
 *
 * Admissions staff can move an application between any of the review stages — real
 * selection is not strictly linear, and a waitlisted applicant is often accepted later. Two
 * stages are special: ENROLLED is only reached through the enrol action, which creates the
 * learner account, and once reached it is final. WITHDRAWN is the applicant's own decision,
 * recorded by staff.
 */
public enum ApplicationStatus {

    SUBMITTED("Application received"),
    SCREENING("Documents being checked"),
    SHORTLISTED("Shortlisted"),
    INTERVIEW("Invited to interview or assessment"),
    ACCEPTED("Accepted"),
    WAITLISTED("On the waiting list"),
    DECLINED("Not successful"),
    WITHDRAWN("Withdrawn"),
    ENROLLED("Enrolled");

    /** Statuses that still hold the applicant's place in the queue for a learnership. */
    public static final Set<ApplicationStatus> ACTIVE =
            EnumSet.of(SUBMITTED, SCREENING, SHORTLISTED, INTERVIEW, ACCEPTED, WAITLISTED, ENROLLED);

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    /** How this reads to an applicant checking their status on the website. */
    public String getLabel() {
        return label;
    }

    /** Whether staff may set this status by hand. ENROLLED only comes from the enrol action. */
    public boolean isManuallySettable() {
        return this != ENROLLED;
    }
}
