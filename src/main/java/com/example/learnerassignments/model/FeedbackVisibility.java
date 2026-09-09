package com.example.learnerassignments.model;

/**
 * Who a piece of feedback is for.
 *
 * Phase 2 only adds and backfills the column. From Phase 5, INTERNAL records — moderator
 * reports — export to the PoE for SETA but never appear in the student portal.
 */
public enum FeedbackVisibility {
    /** Visible to the learner once published. */
    LEARNER,
    /** For the record and the export only. */
    INTERNAL
}
