package com.example.learnerassignments.model;

/**
 * The kinds of thing worth telling somebody about.
 *
 * Deliberately short. A notification the learner cannot act on is noise, and noise is how a
 * badge stops being read — which then hides the one that mattered.
 */
public enum NotificationType {
    /** A facilitator released the marking for a session. */
    FEEDBACK_RELEASED,
    /** A portfolio document was rejected and has to be supplied again. */
    DOCUMENT_REJECTED,
    /** New material was published on a module the learner is enrolled on. */
    GUIDE_PUBLISHED
}
