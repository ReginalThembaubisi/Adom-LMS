package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.PoeCompletenessDtos.ChecklistItem;
import com.example.learnerassignments.dto.PoeCompletenessDtos.CompletenessDashboard;
import com.example.learnerassignments.dto.PoeCompletenessDtos.ItemState;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnerChecklist;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnerRow;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnerState;
import com.example.learnerassignments.dto.PoeCompletenessDtos.MissingItemTally;
import com.example.learnerassignments.dto.PoeCompletenessDtos.UpdateRequirementRequest;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the dashboard is allowed to say.
 *
 * Two of these tests exist because of bugs that shipped: a count that reported rows where people
 * were meant, and a count that silently capped itself. The rest exist because the screen is about
 * to be used to decide whether a portfolio goes to a SETA, and every wrong number on it is either
 * an accusation against a learner or a false reassurance to the provider.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PoeCompletenessTest {

    @Autowired PoeCompletenessService completenessService;
    @Autowired PoeRequirementService requirementService;
    @Autowired PoeRequirementSeed requirementSeed;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired LearnerDocumentRepository documentRepository;
    @Autowired PoeDocumentRequirementRepository requirementRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;

    // ------------------------------------------------------------------ required_from

    @Test
    @DisplayName("A learner who registered before the requirement took effect is exempt, not missing")
    void predatingCohortIsExemptNotRed() {
        Learnership learnership = learnership("Exempt");
        requirementService.seedDefaults(learnership, LocalDate.now().minusDays(1));
        Learner learner = learner(learnership, "COHORT-A", LocalDateTime.now().minusDays(30));

        LearnerChecklist checklist = completenessService.checklistFor(learner.getId());

        assertThat(checklist.getDocumentItems()).isNotEmpty();
        assertThat(checklist.getDocumentItems())
                .allSatisfy(item -> assertThat(item.getState()).isEqualTo(ItemState.EXEMPT));
        assertThat(checklist.getLearner().getDocuments().getItemsMissing()).isZero();
        assertThat(checklist.getLearner().getDocuments().getItemsOutstanding()).isZero();
    }

    @Test
    @DisplayName("An entirely exempt learner is never reported complete")
    void exemptIsNotComplete() {
        Learnership learnership = learnership("NotComplete");
        requirementService.seedDefaults(learnership, LocalDate.now().minusDays(1));
        Learner learner = learner(learnership, "COHORT-A", LocalDateTime.now().minusDays(30));

        LearnerChecklist checklist = completenessService.checklistFor(learner.getId());

        // A SETA auditor will still want the paperwork. Not red, but not green either.
        assertThat(checklist.getLearner().getState()).isEqualTo(LearnerState.NOT_FULLY_IN_SCOPE);
    }

    @Test
    @DisplayName("A learner who registered after the requirement took effect is missing it")
    void laterCohortIsCounted() {
        Learnership learnership = learnership("Counted");
        requirementService.seedDefaults(learnership, LocalDate.now().minusDays(30));
        Learner learner = learner(learnership, "COHORT-B", LocalDateTime.now().minusDays(2));

        LearnerChecklist checklist = completenessService.checklistFor(learner.getId());

        assertThat(checklist.getDocumentItems())
                .allSatisfy(item -> assertThat(item.getState()).isEqualTo(ItemState.MISSING));
        assertThat(checklist.getLearner().getState()).isEqualTo(LearnerState.OUTSTANDING);
    }

    @Test
    @DisplayName("A document supplied by an exempt learner still counts as supplied")
    void exemptLearnerWhoSuppliedIsCredited() {
        Learnership learnership = learnership("Credited");
        requirementService.seedDefaults(learnership, LocalDate.now().minusDays(1));
        Learner learner = learner(learnership, "COHORT-A", LocalDateTime.now().minusDays(30));
        document(learner, PoeDocumentType.CV, ReviewStatus.ACCEPTED);

        ChecklistItem cv = itemFor(completenessService.checklistFor(learner.getId()), "CV");

        // Somebody did the work. Reporting it exempt would lose that.
        assertThat(cv.getState()).isEqualTo(ItemState.ACCEPTED);
    }

    @Test
    @DisplayName("Clearing required_from starts counting the cohort that was exempt")
    void clearingTheDateStartsCounting() {
        Learnership learnership = learnership("Cleared");
        requirementService.seedDefaults(learnership, LocalDate.now().minusDays(1));
        Learner learner = learner(learnership, "COHORT-A", LocalDateTime.now().minusDays(30));

        assertThat(itemFor(completenessService.checklistFor(learner.getId()), "CV").getState())
                .isEqualTo(ItemState.EXEMPT);

        requirementService.update(learnership.getId(), UpdateRequirementRequest.builder()
                .documentType("CV").clearRequiredFrom(true).build(), "admin");

        assertThat(itemFor(completenessService.checklistFor(learner.getId()), "CV").getState())
                .isEqualTo(ItemState.MISSING);
    }

    // ------------------------------------------------------------------ per-learnership requirements

    @Test
    @DisplayName("A requirement a learnership switched off is not on the checklist at all")
    void switchedOffRequirementDisappears() {
        Learnership learnership = learnership("NoMatric");
        requirementService.seedDefaults(learnership, null);
        requirementService.update(learnership.getId(), UpdateRequirementRequest.builder()
                .documentType("MATRIC").required(false).build(), "admin");
        Learner learner = learner(learnership, "COHORT-A", LocalDateTime.now());

        LearnerChecklist checklist = completenessService.checklistFor(learner.getId());

        assertThat(checklist.getDocumentItems()).extracting(ChecklistItem::getKey)
                .doesNotContain("MATRIC")
                .contains("CV", "ID_COPY", "AGREEMENT");
    }

    @Test
    @DisplayName("Two learnerships can require different documents of the same-looking learner")
    void requirementsArePerLearnership() {
        Learnership strict = learnership("Strict");
        Learnership lenient = learnership("Lenient");
        requirementService.seedDefaults(strict, null);
        requirementService.seedDefaults(lenient, null);
        requirementService.update(lenient.getId(), UpdateRequirementRequest.builder()
                .documentType("MATRIC").required(false).build(), "admin");

        Learner strictLearner = learner(strict, "X", LocalDateTime.now());
        Learner lenientLearner = learner(lenient, "X", LocalDateTime.now());

        assertThat(completenessService.checklistFor(strictLearner.getId()).getDocumentItems()).hasSize(4);
        assertThat(completenessService.checklistFor(lenientLearner.getId()).getDocumentItems()).hasSize(3);
    }

    @Test
    @DisplayName("The seed never switches a requirement back on")
    void seedNeverReEnables() {
        Learnership learnership = learnership("Respected");
        requirementService.seedDefaults(learnership, LocalDate.now());
        requirementService.update(learnership.getId(), UpdateRequirementRequest.builder()
                .documentType("MATRIC").required(false).build(), "admin");

        requirementSeed.run();

        PoeDocumentRequirement matric = requirementRepository.findByLearnership_Id(learnership.getId())
                .stream().filter(r -> r.getDocumentType() == PoeDocumentType.MATRIC).findFirst().orElseThrow();
        assertThat(matric.isRequired()).isFalse();
    }

    @Test
    @DisplayName("Seeding twice inserts nothing the second time")
    void seedIsIdempotent() {
        Learnership learnership = learnership("Twice");
        int first = requirementService.seedDefaults(learnership, LocalDate.now());
        int second = requirementService.seedDefaults(learnership, LocalDate.now());

        assertThat(first).isEqualTo(PoeDocumentType.required().size());
        assertThat(second).isZero();
    }

    // ------------------------------------------------------------------ people vs things

    @Test
    @DisplayName("Documents outstanding counts documents; learners outstanding counts learners")
    void peopleAndThingsAreCountedSeparately() {
        Learnership learnership = learnership("Counting");
        requirementService.seedDefaults(learnership, null);

        // Two learners, four required documents each, one document supplied between them.
        Learner one = learner(learnership, "COHORT-C", LocalDateTime.now());
        Learner two = learner(learnership, "COHORT-C", LocalDateTime.now());
        document(one, PoeDocumentType.CV, ReviewStatus.ACCEPTED);

        CompletenessDashboard dashboard = completenessService.dashboard(learnership.getId(), null);

        // Seven documents outstanding across two people. Neither number is the other.
        assertThat(dashboard.getSummary().getDocuments().getItemsOutstanding()).isEqualTo(7);
        assertThat(dashboard.getSummary().getLearnersWithDocumentsOutstanding()).isEqualTo(2);
        assertThat(dashboard.getSummary().getLearnersWithSomethingOutstanding()).isEqualTo(2);
        assertThat(dashboard.getSummary().getLearnersInScope()).isEqualTo(2);
        assertThat(one.getId()).isNotEqualTo(two.getId());
    }

    @Test
    @DisplayName("The learner state counts are exclusive and add up to the learners in scope")
    void stateCountsAddUp() {
        Learnership learnership = learnership("AddUp");
        requirementService.seedDefaults(learnership, LocalDate.now().minusDays(5));

        learner(learnership, "A", LocalDateTime.now());                        // outstanding
        Learner exempt = learner(learnership, "A", LocalDateTime.now().minusDays(30)); // not in scope
        Learner done = learner(learnership, "A", LocalDateTime.now());
        for (PoeDocumentType type : PoeDocumentType.required()) {
            document(done, type, ReviewStatus.ACCEPTED);
        }

        CompletenessDashboard dashboard = completenessService.dashboard(learnership.getId(), null);
        var summary = dashboard.getSummary();

        assertThat(summary.getLearnersComplete()
                + summary.getLearnersAwaitingReview()
                + summary.getLearnersNotFullyInScope()
                + summary.getLearnersWithSomethingOutstanding())
                .isEqualTo(summary.getLearnersInScope());
        assertThat(summary.getLearnersComplete()).isEqualTo(1);
        assertThat(exempt.getId()).isNotNull();
    }

    @Test
    @DisplayName("Most commonly missing counts learners, not rows")
    void mostCommonlyMissingCountsPeople() {
        Learnership learnership = learnership("Tally");
        requirementService.seedDefaults(learnership, null);
        learner(learnership, "T", LocalDateTime.now());
        learner(learnership, "T", LocalDateTime.now());
        Learner third = learner(learnership, "T", LocalDateTime.now());
        document(third, PoeDocumentType.CV, ReviewStatus.ACCEPTED);

        List<MissingItemTally> tallies =
                completenessService.dashboard(learnership.getId(), null).getMostCommonlyMissing();

        MissingItemTally cv = tallies.stream().filter(t -> "CV".equals(t.getKey())).findFirst().orElseThrow();
        assertThat(cv.getLearnersMissingIt()).isEqualTo(2);
        assertThat(tallies).allSatisfy(t -> assertThat(t.getLearnersMissingIt()).isLessThanOrEqualTo(3));
    }

    // ------------------------------------------------------------------ filtering

    @Test
    @DisplayName("The learnership filter excludes learners on other learnerships")
    void learnershipFilterIsHonoured() {
        Learnership mine = learnership("Mine");
        Learnership theirs = learnership("Theirs");
        requirementService.seedDefaults(mine, null);
        requirementService.seedDefaults(theirs, null);
        Learner ours = learner(mine, "M", LocalDateTime.now());
        learner(theirs, "T", LocalDateTime.now());

        CompletenessDashboard dashboard = completenessService.dashboard(mine.getId(), null);

        assertThat(dashboard.getLearners()).extracting(LearnerRow::getLearnerId).containsExactly(ours.getId());
        assertThat(dashboard.getFilters().isMixedLearnerships()).isFalse();
    }

    @Test
    @DisplayName("The cohort filter narrows within a learnership, and offers only its own cohorts")
    void cohortFilterIsHonoured() {
        Learnership learnership = learnership("Cohorts");
        requirementService.seedDefaults(learnership, null);
        Learner january = learner(learnership, "JAN-2026", LocalDateTime.now());
        learner(learnership, "JUL-2026", LocalDateTime.now());

        CompletenessDashboard dashboard = completenessService.dashboard(learnership.getId(), "JAN-2026");

        assertThat(dashboard.getLearners()).extracting(LearnerRow::getLearnerId).containsExactly(january.getId());
        assertThat(dashboard.getFilters().getCohorts()).containsExactly("JAN-2026", "JUL-2026");
        assertThat(dashboard.getFilters().getCohort()).isEqualTo("JAN-2026");
    }

    // ------------------------------------------------------------------ submissions

    @Test
    @DisplayName("A closed session with nothing submitted is missing; an open one is not yet due")
    void submissionCoverageFollowsTheClosingDate() {
        Learnership learnership = learnership("Sessions");
        requirementService.seedDefaults(learnership, null);
        Module module = module(learnership);
        Learner learner = learner(learnership, "S", LocalDateTime.now().minusDays(20));
        enrol(learner, module);

        session(module, "Closed task", LocalDateTime.now().minusDays(2));
        session(module, "Open task", LocalDateTime.now().plusDays(5));

        LearnerChecklist checklist = completenessService.checklistFor(learner.getId());

        assertThat(stateOfItem(checklist, "Closed task")).isEqualTo(ItemState.MISSING);
        assertThat(stateOfItem(checklist, "Open task")).isEqualTo(ItemState.NOT_YET_DUE);
    }

    @Test
    @DisplayName("A session that closed before the learner registered is exempt, not missing")
    void sessionClosedBeforeTheyJoinedIsExempt() {
        Learnership learnership = learnership("Joined");
        requirementService.seedDefaults(learnership, null);
        Module module = module(learnership);
        Learner learner = learner(learnership, "S", LocalDateTime.now().minusDays(3));
        enrol(learner, module);
        session(module, "Before their time", LocalDateTime.now().minusDays(30));

        // The same principle as required_from. It was never theirs to submit to.
        assertThat(stateOfItem(completenessService.checklistFor(learner.getId()), "Before their time"))
                .isEqualTo(ItemState.EXEMPT);
    }

    @Test
    @DisplayName("A submitted session is awaiting marking until it is marked")
    void submittedThenMarked() {
        Learnership learnership = learnership("Marking");
        requirementService.seedDefaults(learnership, null);
        Module module = module(learnership);
        Learner learner = learner(learnership, "S", LocalDateTime.now().minusDays(20));
        enrol(learner, module);
        SubmissionSession session = session(module, "Marked task", LocalDateTime.now().minusDays(1));
        Submission submission = submit(learner, session, null);

        assertThat(stateOfItem(completenessService.checklistFor(learner.getId()), "Marked task"))
                .isEqualTo(ItemState.AWAITING_REVIEW);

        submission.setGradedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        assertThat(stateOfItem(completenessService.checklistFor(learner.getId()), "Marked task"))
                .isEqualTo(ItemState.ACCEPTED);
    }

    @Test
    @DisplayName("A resubmission does not make marked work look unmarked")
    void resubmissionDoesNotUnmarkWork() {
        Learnership learnership = learnership("Resubmit");
        requirementService.seedDefaults(learnership, null);
        Module module = module(learnership);
        Learner learner = learner(learnership, "S", LocalDateTime.now().minusDays(20));
        enrol(learner, module);
        SubmissionSession session = session(module, "Twice task", LocalDateTime.now().minusDays(1));
        submit(learner, session, LocalDateTime.now());
        submit(learner, session, null);

        // Two rows for one session. Sending a facilitator after work already assessed is the
        // failure mode; marked wins.
        assertThat(stateOfItem(completenessService.checklistFor(learner.getId()), "Twice task"))
                .isEqualTo(ItemState.ACCEPTED);
    }

    @Test
    @DisplayName("Sessions on modules the learner is not enrolled on are not expected of them")
    void unenrolledModulesAreNotCounted() {
        Learnership learnership = learnership("Unenrolled");
        requirementService.seedDefaults(learnership, null);
        Module module = module(learnership);
        Learner learner = learner(learnership, "S", LocalDateTime.now().minusDays(20));
        session(module, "Not theirs", LocalDateTime.now().minusDays(2));

        assertThat(completenessService.checklistFor(learner.getId()).getSubmissionItems()).isEmpty();
    }

    // ------------------------------------------------------------------ defensive

    @Test
    @DisplayName("A learner with no learnership is flagged, not silently reported complete")
    void learnerWithoutLearnershipIsFlagged() {
        Learner orphan = learner(null, "ORPHAN", LocalDateTime.now());

        LearnerChecklist checklist = completenessService.checklistFor(orphan.getId());

        assertThat(checklist.getLearner().getState()).isEqualTo(LearnerState.OUTSTANDING);
        assertThat(checklist.getDocumentItems()).extracting(ChecklistItem::getKey)
                .contains("NO_LEARNERSHIP");
    }

    @Test
    @DisplayName("A learnership with no configured requirements over-reports and says so")
    void unconfiguredLearnershipOverReports() {
        Learnership learnership = learnership("Unconfigured");
        Learner learner = learner(learnership, "U", LocalDateTime.now().minusDays(90));

        CompletenessDashboard dashboard = completenessService.dashboard(learnership.getId(), null);

        // Reporting somebody complete because nobody configured anything is the failure that
        // reaches a SETA. Reporting them incomplete when they are fine is a phone call.
        assertThat(dashboard.getLearnershipsWithoutRequirements()).contains(learnership.getName());
        assertThat(dashboard.getSummary().getDocuments().getItemsMissing())
                .isEqualTo(PoeDocumentType.required().size());
        assertThat(learner.getId()).isNotNull();
    }

    @Test
    @DisplayName("A rejected document is outstanding, not accepted and not merely pending")
    void rejectedIsOutstanding() {
        Learnership learnership = learnership("Rejected");
        requirementService.seedDefaults(learnership, null);
        Learner learner = learner(learnership, "R", LocalDateTime.now());
        document(learner, PoeDocumentType.CV, ReviewStatus.REJECTED);

        LearnerChecklist checklist = completenessService.checklistFor(learner.getId());

        assertThat(itemFor(checklist, "CV").getState()).isEqualTo(ItemState.REJECTED);
        assertThat(checklist.getLearner().getDocuments().getItemsOutstanding())
                .isEqualTo(PoeDocumentType.required().size());
    }

    @Test
    @DisplayName("The schema will not let a document exist without a status")
    void statusCannotBeNull() {
        Learnership learnership = learnership("NullStatus");
        requirementService.seedDefaults(learnership, null);
        Learner learner = learner(learnership, "N", LocalDateTime.now());
        LearnerDocument document = document(learner, PoeDocumentType.CV, ReviewStatus.PENDING);
        document.setStatus(null);

        // The service still defaults a null status to AWAITING_REVIEW rather than dropping the
        // row, but this is why that branch is belt-and-braces and not a live path: the column is
        // NOT NULL, so the dashboard cannot be shown a document nobody has classified. Asserting
        // the constraint is worth more than asserting a branch the database forbids reaching.
        assertThatThrownBy(() -> documentRepository.saveAndFlush(document))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("An empty scope produces zeroes, not an exception")
    void emptyScopeIsSafe() {
        Learnership learnership = learnership("Empty");
        requirementService.seedDefaults(learnership, null);

        CompletenessDashboard dashboard = completenessService.dashboard(learnership.getId(), null);

        assertThat(dashboard.getSummary().getLearnersInScope()).isZero();
        assertThat(dashboard.getLearners()).isEmpty();
        assertThat(dashboard.getMostCommonlyMissing()).isEmpty();
    }

    // ------------------------------------------------------------------ fixture

    private Learnership learnership(String name) {
        return learnershipRepository.save(
                Learnership.builder().name(name + " " + System.nanoTime()).build());
    }

    private Learner learner(Learnership learnership, String cohort, LocalDateTime registeredAt) {
        long unique = System.nanoTime();
        return learnerRepository.save(Learner.builder()
                .fullName("Learner " + unique)
                .learnerCode("PC" + unique)
                .email("pc" + unique + "@example.com")
                .phoneNumber("0700000000")
                .cohort(cohort)
                .learnership(learnership)
                .createdAt(registeredAt)
                .modules(new HashSet<>())
                .build());
    }

    private LearnerDocument document(Learner learner, PoeDocumentType type, ReviewStatus status) {
        return documentRepository.save(LearnerDocument.builder()
                .learner(learner)
                .documentType(type)
                .filePath("lms_secure/doc" + System.nanoTime())
                .originalFilename(type.name().toLowerCase() + ".pdf")
                .version(1)
                .current(true)
                .status(status)
                .uploadedAt(LocalDateTime.now())
                .uploadedByRole("LEARNER")
                .build());
    }

    private Module module(Learnership learnership) {
        long unique = System.nanoTime();
        Category category = categoryRepository.save(
                Category.builder().categoryType("CORE").learnership(learnership).build());
        return moduleRepository.save(Module.builder()
                .moduleName("Module " + unique).moduleCode("MC" + unique).category(category).build());
    }

    private void enrol(Learner learner, Module module) {
        Set<Module> modules = learner.getModules() == null ? new HashSet<>() : new HashSet<>(learner.getModules());
        modules.add(module);
        learner.setModules(modules);
        learnerRepository.saveAndFlush(learner);
    }

    private SubmissionSession session(Module module, String name, LocalDateTime endTime) {
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title(name).description("").dueDate(endTime).module(module).build());
        return sessionRepository.save(SubmissionSession.builder()
                .sessionName(name).assignment(assignment)
                .startTime(endTime.minusDays(7)).endTime(endTime)
                .status(SessionStatus.OPEN).build());
    }

    private Submission submit(Learner learner, SubmissionSession session, LocalDateTime gradedAt) {
        return submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath("lms_secure/sub" + System.nanoTime()).originalFilename("work.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED)
                .gradedAt(gradedAt)
                .build());
    }

    private ChecklistItem itemFor(LearnerChecklist checklist, String key) {
        return checklist.getDocumentItems().stream()
                .filter(i -> key.equals(i.getKey())).findFirst().orElseThrow();
    }

    private ItemState stateOfItem(LearnerChecklist checklist, String label) {
        return checklist.getSubmissionItems().stream()
                .filter(i -> label.equals(i.getLabel())).findFirst().orElseThrow().getState();
    }
}
