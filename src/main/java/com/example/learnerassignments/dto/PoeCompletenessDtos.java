package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the completeness dashboard reports.
 *
 * <p><strong>People and things are counted separately, and named so they cannot be confused.</strong>
 * "23 documents outstanding" and "9 learners with something outstanding" are different sentences a
 * facilitator acts on differently — one is a to-do list, the other is a phone list — so every count
 * in here says in its own name which of the two it is. There is no field called {@code outstanding}.
 */
public class PoeCompletenessDtos {

    /**
     * Where one checklist item stands.
     *
     * EXEMPT and NOT_YET_DUE both mean "not counted as missing", and they are kept apart because
     * they are undone by different things: EXEMPT clears when an admin moves a requirement's
     * effective date back, NOT_YET_DUE clears by itself when the session closes.
     */
    public enum ItemState {
        /** Supplied and accepted, or submitted and marked. Green. */
        ACCEPTED,
        /** Supplied, waiting on a reviewer or a marker. Amber — nothing for the learner to do. */
        AWAITING_REVIEW,
        /** Supplied and turned down. Red, and the learner has to act. */
        REJECTED,
        /** Nothing supplied, and it was due. Red. */
        MISSING,
        /** The learner registered before this requirement took effect, or the session closed before
         *  they joined. Not counted as missing — but counted, and shown, as exempt. */
        EXEMPT,
        /** The session is still open. Not outstanding yet. */
        NOT_YET_DUE
    }

    /**
     * A learner's overall position, chosen by the worst thing on their checklist.
     *
     * Mutually exclusive, so the four counts on the summary add up to the number of learners.
     * NOT_FULLY_IN_SCOPE exists so that a learner whose whole checklist is exempt is never
     * reported as COMPLETE: a SETA auditor will still want the documents.
     */
    public enum LearnerState {
        OUTSTANDING,
        AWAITING_REVIEW,
        NOT_FULLY_IN_SCOPE,
        COMPLETE
    }

    /** One row of one learner's checklist. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChecklistItem {
        /** DOCUMENT or SUBMISSION. */
        private String kind;
        /** Stable identity for grouping across learners: the document type, or "session:{id}". */
        private String key;
        /** How it reads to a human. */
        private String label;
        private ItemState state;
        /** Why it is in that state, in a sentence, where the state alone is not enough. */
        private String detail;
        private LocalDateTime at;
    }

    /** Thing-counts for one learner's checklist, or for one section of it. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemCounts {
        private int itemsOnChecklist;
        private int itemsAccepted;
        private int itemsAwaitingReview;
        private int itemsMissing;
        private int itemsRejected;
        private int itemsExempt;
        private int itemsNotYetDue;

        /** Missing plus rejected: what somebody still has to hand in. */
        public int getItemsOutstanding() {
            return itemsMissing + itemsRejected;
        }
    }

    /** One learner as the dashboard lists them. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LearnerRow {
        private Long learnerId;
        private String learnerCode;
        private String fullName;
        private String cohort;
        private String learnershipName;
        private LearnerState state;
        private ItemCounts documents;
        private ItemCounts submissions;
    }

    /** One learner's full checklist, for the drill-down. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LearnerChecklist {
        private LearnerRow learner;
        private List<ChecklistItem> documentItems;
        private List<ChecklistItem> submissionItems;
    }

    /**
     * One thing, and how many learners are missing it.
     *
     * {@code learnersMissingIt} counts people, not rows — which is the whole point of the field.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MissingItemTally {
        private String kind;
        private String key;
        private String label;
        private int learnersMissingIt;
    }

    /**
     * The headline numbers.
     *
     * The four {@code learners*} state counts are mutually exclusive and sum to
     * {@code learnersInScope}. The two {@code learnersWith*Outstanding} counts are not exclusive
     * of each other — somebody short a document and a submission appears in both — and are
     * labelled as an overlapping breakdown wherever they are shown.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CompletenessSummary {
        // People.
        private int learnersInScope;
        private int learnersComplete;
        private int learnersAwaitingReview;
        private int learnersNotFullyInScope;
        private int learnersWithSomethingOutstanding;
        private int learnersWithDocumentsOutstanding;
        private int learnersWithSubmissionsOutstanding;

        // Things.
        private ItemCounts documents;
        private ItemCounts submissions;
    }

    /** What the filter row offers, and what it is currently set to. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardFilters {
        private Long learnershipId;
        private String learnershipName;
        private String cohort;
        private List<FilterOption> learnerships;
        private List<String> cohorts;
        /**
         * True when more than one learnership is in scope at once. The screen says so, because
         * "how many are complete" across two qualifications with different requirements is not a
         * number anybody should act on.
         */
        private boolean mixedLearnerships;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FilterOption {
        private Long id;
        private String name;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CompletenessDashboard {
        private DashboardFilters filters;
        private CompletenessSummary summary;
        private List<MissingItemTally> mostCommonlyMissing;
        private List<LearnerRow> learners;
        /** Learnerships in scope that have no requirements configured at all. */
        private List<String> learnershipsWithoutRequirements;
        private LocalDateTime generatedAt;
    }

    /** One learnership's configured requirements, for the admin to read and edit. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RequirementRow {
        private Long id;
        private String documentType;
        private String documentLabel;
        private int poeSection;
        private boolean required;
        private java.time.LocalDate requiredFrom;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LearnershipRequirements {
        private Long learnershipId;
        private String learnershipName;
        private List<RequirementRow> requirements;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequirementRequest {
        private String documentType;
        private Boolean required;
        private java.time.LocalDate requiredFrom;
        /** True clears the date so the requirement applies to everybody, past cohorts included. */
        private boolean clearRequiredFrom;
    }
}
