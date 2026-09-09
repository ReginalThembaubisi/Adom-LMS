package com.example.learnerassignments.model;

/** What a moderator's assignment covers. Neither value widens the rows themselves. */
public enum ModerationScope {
    /** Everything the assignment's learnership and cohort cover. */
    FULL,
    /** A moderation sample drawn from it. */
    SAMPLE
}
