package com.example.learnerassignments.model;

/** How confident the importer is that a folder maps to a given learner. */
public enum LegacyImportMatchConfidence {
    /** Normalised folder name equals the learner's full name exactly. */
    EXACT,
    /** Every word in the folder name appears in the learner's full name, or vice versa —
     *  "AMANDA MNDAWE" against "Amanda Randy Mndawe". Never auto-committed without review. */
    LIKELY,
    /** No learner, or more than one, fits the folder name. Requires a human to resolve. */
    UNMATCHED,
    /** The zip was a single learner's folder with no name to match — the admin named the
     *  learner directly when requesting the preview. */
    SPECIFIED
}
