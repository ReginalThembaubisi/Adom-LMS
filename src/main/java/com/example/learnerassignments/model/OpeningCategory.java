package com.example.learnerassignments.model;

/**
 * The Careers page's categories. Applications to an internship opening are INTERNSHIP
 * applications; every other category is a JOB application.
 */
public enum OpeningCategory {

    INTERNSHIP("Internships"),
    GRADUATE_PROGRAMME("Graduate programmes"),
    GRADE_12("Grade 12 holders"),
    ENTRY_LEVEL("Entry-level jobs"),
    GOVERNMENT("Government jobs");

    private final String label;

    OpeningCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public ApplicationType applicationType() {
        return this == INTERNSHIP ? ApplicationType.INTERNSHIP : ApplicationType.JOB;
    }
}
