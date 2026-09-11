package com.example.learnerassignments.model;

/** The lifecycle of one legacy-folder import attempt. */
public enum LegacyImportStatus {
    /** A zip has been parsed and matched; nothing has been written to any learner's vault yet. */
    PREVIEWED,
    /** An admin confirmed the preview; matched, importable entries were written. */
    CONFIRMED,
    /** An admin cancelled the preview. Nothing was ever written. */
    CANCELLED
}
