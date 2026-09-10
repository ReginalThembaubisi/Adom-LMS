package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.PoeCompletenessDtos.ChecklistItem;
import com.example.learnerassignments.dto.PoeCompletenessDtos.CompletenessDashboard;
import com.example.learnerassignments.dto.PoeCompletenessDtos.CompletenessSummary;
import com.example.learnerassignments.dto.PoeCompletenessDtos.DashboardFilters;
import com.example.learnerassignments.dto.PoeCompletenessDtos.FilterOption;
import com.example.learnerassignments.dto.PoeCompletenessDtos.ItemCounts;
import com.example.learnerassignments.dto.PoeCompletenessDtos.ItemState;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnerChecklist;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnerRow;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnerState;
import com.example.learnerassignments.dto.PoeCompletenessDtos.MissingItemTally;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LearnerDocument;
import com.example.learnerassignments.model.Learnership;
import com.example.learnerassignments.model.PoeDocumentRequirement;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.model.ReviewStatus;
import com.example.learnerassignments.repository.LearnerDocumentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LearnershipRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.repository.SubmissionSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * How complete each learner's portfolio is, and how complete a cohort is.
 *
 * <p>Two rules run through the whole class, both of them learned the expensive way.
 *
 * <p><strong>People and things are counted separately.</strong> "23 documents outstanding" is a
 * to-do list; "9 learners with something outstanding" is a phone list. Every count below carries
 * its unit in its name, and no number is ever reported without one.
 *
 * <p><strong>Not counted as missing is not the same as fine.</strong> A learner who registered
 * before a requirement took effect, or before a submission session closed, was never asked — so
 * they are reported EXEMPT, not MISSING and not ACCEPTED. Exempt items keep a learner out of
 * COMPLETE, because a SETA auditor will still want the paperwork.
 *
 * <p>Everything here is null-defensive: the Phase 2 columns are nullable on tables that already
 * held rows, learners can have no learnership, and sessions can in principle have no closing date.
 * A null must never quietly drop a row from a count — a missing row reads as reassurance, which is
 * worse than no number at all.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoeCompletenessService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    /** A learner with no learnership cannot be reported against any requirement set at all. */
    private static final String NO_LEARNERSHIP_KEY = "NO_LEARNERSHIP";
    private static final String NO_LEARNERSHIP_LABEL = "Learnership not assigned";

    private final LearnerRepository learnerRepository;
    private final LearnerDocumentRepository documentRepository;
    private final LearnershipRepository learnershipRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final PoeRequirementService requirementService;

    // ------------------------------------------------------------------ public API

    @Transactional(readOnly = true)
    public CompletenessDashboard dashboard(Long learnershipId, String cohort) {
        List<Learner> inLearnership = learnershipId == null
                ? learnerRepository.findAll()
                : learnerRepository.findByLearnership_Id(learnershipId);

        // Cohort options come from the learnership in scope, not from every learner in the
        // database: offering a cohort that belongs to another qualification only invites a filter
        // combination that returns nothing and reads like a bug.
        Set<String> cohortOptions = new TreeSet<>();
        inLearnership.stream()
                .map(Learner::getCohort)
                .filter(c -> c != null && !c.isBlank())
                .forEach(cohortOptions::add);

        String wantedCohort = (cohort == null || cohort.isBlank()) ? null : cohort;
        List<Learner> learners = wantedCohort == null
                ? inLearnership
                : inLearnership.stream().filter(l -> wantedCohort.equals(l.getCohort())).toList();

        Set<String> unconfigured = new LinkedHashSet<>();
        List<LearnerChecklist> checklists = buildChecklists(learners, unconfigured);

        List<LearnerRow> rows = checklists.stream().map(LearnerChecklist::getLearner).toList();

        Learnership selected = learnershipId == null
                ? null
                : learnershipRepository.findById(learnershipId).orElse(null);

        long distinctLearnerships = rows.stream()
                .map(LearnerRow::getLearnershipName)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        DashboardFilters filters = DashboardFilters.builder()
                .learnershipId(learnershipId)
                .learnershipName(selected == null ? null : selected.getName())
                .cohort(wantedCohort)
                .learnerships(learnershipRepository.findAll().stream()
                        .map(l -> FilterOption.builder().id(l.getId()).name(l.getName()).build())
                        .sorted(Comparator.comparing(FilterOption::getName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                        .toList())
                .cohorts(new ArrayList<>(cohortOptions))
                .mixedLearnerships(distinctLearnerships > 1)
                .build();

        return CompletenessDashboard.builder()
                .filters(filters)
                .summary(summarise(checklists))
                .mostCommonlyMissing(tallyMissing(checklists))
                .learners(sortWorstFirst(rows))
                .learnershipsWithoutRequirements(new ArrayList<>(unconfigured))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /** One learner's full checklist. Returns null when the learner does not exist. */
    @Transactional(readOnly = true)
    public LearnerChecklist checklistFor(Long learnerId) {
        Learner learner = learnerRepository.findById(learnerId).orElse(null);
        if (learner == null) {
            return null;
        }
        List<LearnerChecklist> built = buildChecklists(List.of(learner), new HashSet<>());
        return built.isEmpty() ? null : built.get(0);
    }

    // ------------------------------------------------------------------ the calculation

    private List<LearnerChecklist> buildChecklists(List<Learner> learners, Set<String> unconfigured) {
        if (learners.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> learnerIds = learners.stream().map(Learner::getId).toList();

        Map<Long, Map<PoeDocumentType, LearnerDocument>> documents = currentDocuments(learnerIds);
        Map<Long, Set<Long>> modulesByLearner = enrolledModules(learnerIds);
        Map<Long, List<SessionInfo>> sessionsByModule = sessionsFor(modulesByLearner);
        Map<Long, Map<Long, Boolean>> markingByLearner = submissionMarking(learnerIds);

        Set<Long> learnershipIds = new HashSet<>();
        for (Learner learner : learners) {
            if (learner.getLearnership() != null) {
                learnershipIds.add(learner.getLearnership().getId());
            }
        }
        Map<Long, List<PoeDocumentRequirement>> requirements =
                requirementService.requirementsByLearnership(learnershipIds);

        List<LearnerChecklist> checklists = new ArrayList<>(learners.size());
        for (Learner learner : learners) {
            List<ChecklistItem> documentItems = documentItems(learner, documents, requirements, unconfigured);
            List<ChecklistItem> submissionItems =
                    submissionItems(learner, modulesByLearner, sessionsByModule, markingByLearner, now);

            ItemCounts documentCounts = count(documentItems);
            ItemCounts submissionCounts = count(submissionItems);

            LearnerRow row = LearnerRow.builder()
                    .learnerId(learner.getId())
                    .learnerCode(learner.getLearnerCode())
                    .fullName(learner.getFullName())
                    .cohort(learner.getCohort())
                    .learnershipName(learner.getLearnership() == null ? null : learner.getLearnership().getName())
                    .state(stateOf(documentCounts, submissionCounts))
                    .documents(documentCounts)
                    .submissions(submissionCounts)
                    .build();

            checklists.add(LearnerChecklist.builder()
                    .learner(row)
                    .documentItems(documentItems)
                    .submissionItems(submissionItems)
                    .build());
        }
        return checklists;
    }

    private List<ChecklistItem> documentItems(Learner learner,
                                              Map<Long, Map<PoeDocumentType, LearnerDocument>> documents,
                                              Map<Long, List<PoeDocumentRequirement>> requirements,
                                              Set<String> unconfigured) {
        List<ChecklistItem> items = new ArrayList<>();
        Learnership learnership = learner.getLearnership();

        if (learnership == null) {
            // Not a document problem, but it belongs on the document checklist: without a
            // learnership there is no requirement set to measure this learner against, and
            // reporting them complete because nothing was required of them would be a lie the
            // dashboard tells right up until the SETA submission.
            unconfigured.add("(no learnership assigned)");
            items.add(ChecklistItem.builder()
                    .kind("DOCUMENT")
                    .key(NO_LEARNERSHIP_KEY)
                    .label(NO_LEARNERSHIP_LABEL)
                    .state(ItemState.MISSING)
                    .detail("This learner is not on a learnership, so no document requirements apply "
                            + "to them and their portfolio cannot be checked.")
                    .build());
            return items;
        }

        List<PoeDocumentRequirement> forLearnership = requirements.get(learnership.getId());
        if (forLearnership == null || forLearnership.isEmpty()) {
            unconfigured.add(learnership.getName());
            forLearnership = requirementService.fallbackRequirements(learnership);
        }

        Map<PoeDocumentType, LearnerDocument> mine =
                documents.getOrDefault(learner.getId(), Map.of());

        List<PoeDocumentRequirement> ordered = new ArrayList<>(forLearnership);
        ordered.sort(Comparator
                .comparingInt((PoeDocumentRequirement r) -> r.getDocumentType().getPoeSection())
                .thenComparing(r -> r.getDocumentType().getLabel()));

        for (PoeDocumentRequirement requirement : ordered) {
            if (!requirement.isRequired() || requirement.getDocumentType() == null) {
                continue;
            }
            PoeDocumentType type = requirement.getDocumentType();
            LearnerDocument document = mine.get(type);

            ItemState state;
            String detail;
            LocalDateTime at = null;

            if (document != null) {
                // A document that was supplied counts on its own terms even if the learner was
                // exempt from supplying it. Somebody did the work; the dashboard should say so.
                at = document.getUploadedAt();
                ReviewStatus status = document.getStatus();
                if (status == ReviewStatus.ACCEPTED) {
                    state = ItemState.ACCEPTED;
                    detail = "Accepted";
                } else if (status == ReviewStatus.REJECTED) {
                    state = ItemState.REJECTED;
                    detail = document.getReviewNote() == null || document.getReviewNote().isBlank()
                            ? "Sent back for resupply"
                            : "Sent back: " + document.getReviewNote();
                } else {
                    // Null status included: a row with no status has been supplied and not yet
                    // judged, which is exactly what awaiting review means.
                    state = ItemState.AWAITING_REVIEW;
                    detail = "Uploaded, waiting on review";
                }
            } else if (requirement.predatesRequirement(learner.getCreatedAt())) {
                state = ItemState.EXEMPT;
                detail = "Required from " + requirement.getRequiredFrom().format(DAY)
                        + "; this learner registered before that and has not been asked for it.";
            } else {
                state = ItemState.MISSING;
                detail = "Not supplied";
            }

            items.add(ChecklistItem.builder()
                    .kind("DOCUMENT")
                    .key(type.name())
                    .label(type.getLabel())
                    .state(state)
                    .detail(detail)
                    .at(at)
                    .build());
        }
        return items;
    }

    private List<ChecklistItem> submissionItems(Learner learner,
                                                Map<Long, Set<Long>> modulesByLearner,
                                                Map<Long, List<SessionInfo>> sessionsByModule,
                                                Map<Long, Map<Long, Boolean>> markingByLearner,
                                                LocalDateTime now) {
        List<ChecklistItem> items = new ArrayList<>();
        Set<Long> moduleIds = modulesByLearner.getOrDefault(learner.getId(), Set.of());
        Map<Long, Boolean> marking = markingByLearner.getOrDefault(learner.getId(), Map.of());
        Set<Long> seen = new HashSet<>();

        for (Long moduleId : moduleIds) {
            for (SessionInfo session : sessionsByModule.getOrDefault(moduleId, List.of())) {
                if (!seen.add(session.id())) {
                    continue;
                }
                Boolean marked = marking.get(session.id());

                ItemState state;
                String detail;
                if (marked != null) {
                    state = marked ? ItemState.ACCEPTED : ItemState.AWAITING_REVIEW;
                    detail = marked ? "Submitted and marked" : "Submitted, waiting on marking";
                } else if (session.endTime() == null) {
                    state = ItemState.NOT_YET_DUE;
                    detail = "No closing date recorded for this session, so it is not counted as due.";
                } else if (session.endTime().isAfter(now)) {
                    state = ItemState.NOT_YET_DUE;
                    detail = "Closes " + session.endTime().format(DAY);
                } else if (learner.getCreatedAt() != null && session.endTime().isBefore(learner.getCreatedAt())) {
                    // The same principle as required_from, applied to work: a session that closed
                    // before somebody joined was never theirs to submit to.
                    state = ItemState.EXEMPT;
                    detail = "Closed " + session.endTime().format(DAY)
                            + ", before this learner registered.";
                } else {
                    state = ItemState.MISSING;
                    detail = "Closed " + session.endTime().format(DAY) + " with nothing submitted";
                }

                items.add(ChecklistItem.builder()
                        .kind("SUBMISSION")
                        .key("session:" + session.id())
                        .label(session.name() == null ? "Session " + session.id() : session.name())
                        .state(state)
                        .detail(detail)
                        .at(session.endTime())
                        .build());
            }
        }
        items.sort(Comparator.comparing(ChecklistItem::getAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    // ------------------------------------------------------------------ counting

    private ItemCounts count(List<ChecklistItem> items) {
        ItemCounts counts = ItemCounts.builder().itemsOnChecklist(items.size()).build();
        for (ChecklistItem item : items) {
            switch (item.getState()) {
                case ACCEPTED -> counts.setItemsAccepted(counts.getItemsAccepted() + 1);
                case AWAITING_REVIEW -> counts.setItemsAwaitingReview(counts.getItemsAwaitingReview() + 1);
                case REJECTED -> counts.setItemsRejected(counts.getItemsRejected() + 1);
                case MISSING -> counts.setItemsMissing(counts.getItemsMissing() + 1);
                case EXEMPT -> counts.setItemsExempt(counts.getItemsExempt() + 1);
                case NOT_YET_DUE -> counts.setItemsNotYetDue(counts.getItemsNotYetDue() + 1);
            }
        }
        return counts;
    }

    private LearnerState stateOf(ItemCounts documents, ItemCounts submissions) {
        if (documents.getItemsOutstanding() + submissions.getItemsOutstanding() > 0) {
            return LearnerState.OUTSTANDING;
        }
        if (documents.getItemsAwaitingReview() + submissions.getItemsAwaitingReview() > 0) {
            return LearnerState.AWAITING_REVIEW;
        }
        if (documents.getItemsExempt() + submissions.getItemsExempt()
                + documents.getItemsNotYetDue() + submissions.getItemsNotYetDue() > 0) {
            return LearnerState.NOT_FULLY_IN_SCOPE;
        }
        return LearnerState.COMPLETE;
    }

    private CompletenessSummary summarise(List<LearnerChecklist> checklists) {
        ItemCounts documents = ItemCounts.builder().build();
        ItemCounts submissions = ItemCounts.builder().build();

        int complete = 0;
        int awaiting = 0;
        int notInScope = 0;
        int outstanding = 0;
        int withDocumentsOutstanding = 0;
        int withSubmissionsOutstanding = 0;

        for (LearnerChecklist checklist : checklists) {
            LearnerRow row = checklist.getLearner();
            add(documents, row.getDocuments());
            add(submissions, row.getSubmissions());

            switch (row.getState()) {
                case COMPLETE -> complete++;
                case AWAITING_REVIEW -> awaiting++;
                case NOT_FULLY_IN_SCOPE -> notInScope++;
                case OUTSTANDING -> outstanding++;
            }
            if (row.getDocuments().getItemsOutstanding() > 0) {
                withDocumentsOutstanding++;
            }
            if (row.getSubmissions().getItemsOutstanding() > 0) {
                withSubmissionsOutstanding++;
            }
        }

        return CompletenessSummary.builder()
                .learnersInScope(checklists.size())
                .learnersComplete(complete)
                .learnersAwaitingReview(awaiting)
                .learnersNotFullyInScope(notInScope)
                .learnersWithSomethingOutstanding(outstanding)
                .learnersWithDocumentsOutstanding(withDocumentsOutstanding)
                .learnersWithSubmissionsOutstanding(withSubmissionsOutstanding)
                .documents(documents)
                .submissions(submissions)
                .build();
    }

    private void add(ItemCounts into, ItemCounts from) {
        into.setItemsOnChecklist(into.getItemsOnChecklist() + from.getItemsOnChecklist());
        into.setItemsAccepted(into.getItemsAccepted() + from.getItemsAccepted());
        into.setItemsAwaitingReview(into.getItemsAwaitingReview() + from.getItemsAwaitingReview());
        into.setItemsRejected(into.getItemsRejected() + from.getItemsRejected());
        into.setItemsMissing(into.getItemsMissing() + from.getItemsMissing());
        into.setItemsExempt(into.getItemsExempt() + from.getItemsExempt());
        into.setItemsNotYetDue(into.getItemsNotYetDue() + from.getItemsNotYetDue());
    }

    /**
     * The most commonly missing items, counted in learners.
     *
     * Deliberately people-per-thing rather than rows: "18 learners have not supplied a CV" is the
     * sentence that gets acted on, and counting rows here would produce the same number by
     * coincidence today and a different one the moment anything can be missing twice.
     */
    private List<MissingItemTally> tallyMissing(List<LearnerChecklist> checklists) {
        Map<String, MissingItemTally> tallies = new HashMap<>();
        for (LearnerChecklist checklist : checklists) {
            Set<String> countedForThisLearner = new HashSet<>();
            List<ChecklistItem> all = new ArrayList<>(checklist.getDocumentItems());
            all.addAll(checklist.getSubmissionItems());
            for (ChecklistItem item : all) {
                if (item.getState() != ItemState.MISSING && item.getState() != ItemState.REJECTED) {
                    continue;
                }
                if (!countedForThisLearner.add(item.getKey())) {
                    continue;
                }
                MissingItemTally tally = tallies.computeIfAbsent(item.getKey(), k ->
                        MissingItemTally.builder()
                                .kind(item.getKind())
                                .key(item.getKey())
                                .label(item.getLabel())
                                .learnersMissingIt(0)
                                .build());
                tally.setLearnersMissingIt(tally.getLearnersMissingIt() + 1);
            }
        }
        return tallies.values().stream()
                .sorted(Comparator.comparingInt(MissingItemTally::getLearnersMissingIt).reversed()
                        .thenComparing(MissingItemTally::getLabel,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(8)
                .toList();
    }

    /** Worst first: the point of the screen is the phone list, not the alphabet. */
    private List<LearnerRow> sortWorstFirst(List<LearnerRow> rows) {
        return rows.stream()
                .sorted(Comparator
                        .comparingInt((LearnerRow r) -> r.getState().ordinal())
                        .thenComparing(r -> -(r.getDocuments().getItemsOutstanding()
                                + r.getSubmissions().getItemsOutstanding()))
                        .thenComparing(LearnerRow::getFullName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    // ------------------------------------------------------------------ bulk reads

    private Map<Long, Map<PoeDocumentType, LearnerDocument>> currentDocuments(Collection<Long> learnerIds) {
        Map<Long, Map<PoeDocumentType, LearnerDocument>> byLearner = new HashMap<>();
        // Ordered by version ascending, so the last row written for a (learner, type) wins if bad
        // data ever leaves two rows current at once.
        for (LearnerDocument document : documentRepository.findCurrentForLearners(learnerIds)) {
            if (document.getLearner() == null || document.getDocumentType() == null) {
                continue;
            }
            byLearner
                    .computeIfAbsent(document.getLearner().getId(), k -> new HashMap<>())
                    .put(document.getDocumentType(), document);
        }
        return byLearner;
    }

    private Map<Long, Set<Long>> enrolledModules(Collection<Long> learnerIds) {
        Map<Long, Set<Long>> byLearner = new HashMap<>();
        for (Object[] pair : learnerRepository.findLearnerModulePairs(learnerIds)) {
            byLearner.computeIfAbsent((Long) pair[0], k -> new HashSet<>()).add((Long) pair[1]);
        }
        return byLearner;
    }

    private Map<Long, List<SessionInfo>> sessionsFor(Map<Long, Set<Long>> modulesByLearner) {
        Set<Long> moduleIds = new HashSet<>();
        modulesByLearner.values().forEach(moduleIds::addAll);
        if (moduleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SessionInfo>> byModule = new HashMap<>();
        for (Object[] row : sessionRepository.findSessionModulePairs(moduleIds)) {
            SessionInfo info = new SessionInfo(
                    (Long) row[0], (String) row[1], (LocalDateTime) row[2]);
            byModule.computeIfAbsent((Long) row[3], k -> new ArrayList<>()).add(info);
        }
        return byModule;
    }

    /** learnerId -> sessionId -> whether that submission has been marked. */
    private Map<Long, Map<Long, Boolean>> submissionMarking(Collection<Long> learnerIds) {
        Map<Long, Map<Long, Boolean>> byLearner = new HashMap<>();
        for (Object[] row : submissionRepository.findLearnerSessionMarkingPairs(learnerIds)) {
            Long learnerId = (Long) row[0];
            Long sessionId = (Long) row[1];
            boolean marked = row[2] != null;
            Map<Long, Boolean> forLearner = byLearner.computeIfAbsent(learnerId, k -> new HashMap<>());
            // A resubmission leaves more than one row for the same session. Marked wins: the work
            // has been assessed, and reporting it as still waiting would send a facilitator after
            // something already done.
            forLearner.merge(sessionId, marked, (a, b) -> a || b);
        }
        return byLearner;
    }

    private record SessionInfo(Long id, String name, LocalDateTime endTime) { }
}
