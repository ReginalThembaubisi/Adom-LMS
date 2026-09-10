package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.SessionSubmissionOverviewResponse;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A submission that exists must always be visible to whoever marks it.
 *
 * The grading list used to be built by walking the module's enrolled learners and attaching
 * their submissions, which quietly assumed that everyone who can submit is in the
 * learner_modules join table. Nothing enforces that: registration does not write those rows,
 * and submitAssignment does not check them. A learner scoped to a module by their learnership
 * — which is how the portal decides what they can see — could therefore submit successfully,
 * be told "Submitted", and never appear in the facilitator's console. The work was in the
 * database and nobody could mark it.
 *
 * Deriving the list from the submissions instead means no enrolment bookkeeping can hide one.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionSubmissionsVisibilityTest {

    @Autowired SubmissionService submissionService;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;

    @Test
    @DisplayName("A learner who is not in the module join table still shows up once they submit")
    void submissionIsVisibleWithoutAJoinTableRow() {
        Fixture f = seed(false);

        SessionSubmissionOverviewResponse overview =
                submissionService.getSessionSubmissionsOverview(f.sessionId, null);

        assertThat(overview.getSubmitted())
                .as("the submission exists, so the marker must be able to see it")
                .hasSize(1);
        assertThat(overview.getSubmitted().get(0).getLearnerCode()).isEqualTo("VIS001");
        assertThat(overview.getSubmitted().get(0).getOriginalFilename()).isEqualTo("essay.pdf");
    }

    @Test
    @DisplayName("An enrolled learner's submission shows up too — the ordinary case still works")
    void submissionIsVisibleWithAJoinTableRow() {
        Fixture f = seed(true);

        SessionSubmissionOverviewResponse overview =
                submissionService.getSessionSubmissionsOverview(f.sessionId, null);

        assertThat(overview.getSubmitted()).hasSize(1);
        assertThat(overview.getUnsubmitted()).isEmpty();
    }

    @Test
    @DisplayName("An enrolled learner who has not submitted is still listed as outstanding")
    void enrolledNonSubmitterIsListedAsUnsubmitted() {
        Fixture f = seed(true);
        submissionRepository.deleteAll();

        SessionSubmissionOverviewResponse overview =
                submissionService.getSessionSubmissionsOverview(f.sessionId, null);

        // The roster is still what tells a facilitator who is missing; only the submitted side
        // stops depending on it.
        assertThat(overview.getSubmitted()).isEmpty();
        assertThat(overview.getUnsubmitted()).hasSize(1);
    }

    @Test
    @DisplayName("Assessor and moderator scoping still narrows the list")
    void scopingStillApplies() {
        Fixture f = seed(false);

        // Phase 1's rule: someone holding no assignment rows reaches nothing. Making
        // submissions visible must not make them visible to everyone.
        SessionSubmissionOverviewResponse none =
                submissionService.getSessionSubmissionsOverview(f.sessionId, Set.of());
        assertThat(none.getSubmitted()).isEmpty();

        SessionSubmissionOverviewResponse scoped =
                submissionService.getSessionSubmissionsOverview(f.sessionId, Set.of(f.learnerId));
        assertThat(scoped.getSubmitted()).hasSize(1);
    }

    private record Fixture(Long sessionId, Long learnerId) {}

    private Fixture seed(boolean enrolInModule) {
        long unique = System.nanoTime();

        Learnership learnership = learnershipRepository.save(
                Learnership.builder().name("Visibility " + unique).build());

        Category category = categoryRepository.save(
                Category.builder().categoryType("CORE").learnership(learnership).build());

        Module module = moduleRepository.save(Module.builder()
                .moduleName("Visibility Module " + unique)
                .moduleCode("VM" + unique)
                .category(category)
                .build());

        Learner learner = Learner.builder()
                .fullName("Visible Learner")
                .learnerCode("VIS001")
                .email("vis" + unique + "@example.com")
                .phoneNumber("0700000000")
                .learnership(learnership)
                .build();
        if (enrolInModule) {
            learner.setModules(new HashSet<>(Set.of(module)));
        }
        learner = learnerRepository.save(learner);

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Visibility Task")
                .description("")
                .dueDate(LocalDateTime.now().plusDays(7))
                .module(module)
                .build());

        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Visibility Session")
                .assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN)
                .build());

        submissionRepository.save(Submission.builder()
                .learner(learner)
                .session(session)
                .filePath("lms_secure/1730_visible")
                .originalFilename("essay.pdf")
                .submittedAt(LocalDateTime.now())
                .status(SubmissionStatus.SUBMITTED)
                .build());

        return new Fixture(session.getId(), learner.getId());
    }
}
