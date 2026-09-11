package com.example.learnerassignments.model;

/**
 * What a SETA export covers.
 *
 * The learnership scope is the one SETA actually asks for — verification happens per
 * qualification — so it is the one every admin screen leads with. The others exist for the
 * smaller, more frequent jobs: a single learner whose folder needs re-checking, a cohort inside
 * a bigger learnership, one section re-run because only that folder needed fixing, or a
 * moderator's or assessor's own assigned slice.
 */
public enum ExportScopeType {
    /** One learner's whole portfolio. */
    LEARNER,
    /** Every learner in one cohort within one learnership. */
    COHORT,
    /** Every learner in one learnership — the scope SETA verification actually asks for. */
    LEARNERSHIP,
    /** One category (Fundamental/Core/Elective) across a learnership's learners. */
    SECTION,
    /**
     * Whatever one assessor or moderator can currently reach.
     *
     * Resolved through {@link com.example.learnerassignments.service.ScopeService} exactly as
     * their normal reads are, so "an assessor's export contains only their assigned learners"
     * holds by construction rather than by a second copy of the scoping rule that could drift
     * from the first.
     */
    MODERATION_SAMPLE
}
