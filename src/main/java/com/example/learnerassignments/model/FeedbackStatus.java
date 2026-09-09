package com.example.learnerassignments.model;

/**
 * Whether marking on a submission has been released to the learner.
 *
 * Phase 2 only adds and backfills the column — nothing reads it yet. Phase 5 makes marking
 * write DRAFT and adds the action that moves a whole session to PUBLISHED.
 */
public enum FeedbackStatus {
    /** Marked but not yet released. Invisible to the learner from Phase 5 onwards. */
    DRAFT,
    /** Released to the learner. */
    PUBLISHED
}
