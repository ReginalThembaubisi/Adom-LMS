package com.example.learnerassignments.model;

/**
 * What kind of row a {@link SignatureEvent} attests to.
 *
 * The implementation brief's sketch also lists {@code MODERATION_REPORT}. It is left out here:
 * a moderator's judgement lives on a {@code SubmissionGradingHistory} row, distinguished from a
 * facilitator's only by {@code feedbackVisibility == INTERNAL}, not as a document of record with
 * its own identity the way a {@link Submission} or {@link LearnerDocument} is. Signing "the
 * grading history row" would sign a log entry, not an artifact — a different feature than this
 * phase builds. Revisit if moderation reports ever become their own entity.
 */
public enum SignableType {
    SUBMISSION,
    LEARNER_DOCUMENT
}
