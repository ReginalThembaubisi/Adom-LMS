package com.example.learnerassignments.model;

/**
 * What someone is applying for through the website. Every type flows through the same
 * pipeline and the same admin screen; only learnership applicants can be enrolled on the LMS,
 * because the LMS is organised around learnerships.
 */
public enum ApplicationType {

    COURSE("Course"),
    LEARNERSHIP("Learnership"),
    INTERNSHIP("Internship"),
    JOB("Job"),
    /** Work-integrated learning: a university student's final-year workplace period. */
    PLACEMENT("University placement");

    private final String label;

    ApplicationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
