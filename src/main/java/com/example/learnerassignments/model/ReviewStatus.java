package com.example.learnerassignments.model;

/** Where a supplied document has got to in review. */
public enum ReviewStatus {
    /** Uploaded, nobody has looked at it yet. */
    PENDING,
    /** Checked and accepted into the portfolio. */
    ACCEPTED,
    /** Sent back. The reason is in reviewNote, and the learner is shown it. */
    REJECTED
}
