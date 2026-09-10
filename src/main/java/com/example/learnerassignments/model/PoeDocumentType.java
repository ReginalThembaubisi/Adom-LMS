package com.example.learnerassignments.model;

import java.util.Arrays;
import java.util.List;

/**
 * The personal documents a learner supplies themselves, and where each belongs in the SETA
 * folder structure.
 *
 * Sections 1 and 2 are the required ones — the paperwork a portfolio is incomplete without.
 * Section 6 exists so that a loose file has somewhere to go rather than being guessed into a
 * slot it does not belong in.
 */
public enum PoeDocumentType {

    CV(1, true, "CV"),
    ID_COPY(1, true, "ID Copy"),

    AGREEMENT(2, true, "Learnership Agreement"),
    MATRIC(2, true, "Matric Certificate"),

    OTHER(6, false, "Other Evidence");

    private final int poeSection;
    private final boolean required;
    private final String label;

    PoeDocumentType(int poeSection, boolean required, String label) {
        this.poeSection = poeSection;
        this.required = required;
        this.label = label;
    }

    public int getPoeSection() {
        return poeSection;
    }

    public boolean isRequired() {
        return required;
    }

    /** How this reads to a learner, who has never heard of an enum constant. */
    public String getLabel() {
        return label;
    }

    /** The four a learner must supply, in the order the portfolio expects them. */
    public static List<PoeDocumentType> required() {
        return Arrays.stream(values()).filter(PoeDocumentType::isRequired).toList();
    }
}
