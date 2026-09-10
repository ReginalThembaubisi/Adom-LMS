package com.example.learnerassignments.service;

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
 * The roster has to contain everyone it claims to, and the backfill has to be safe to run on
 * every boot — it runs on every boot.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearnerModuleEnrolmentBackfillTest {

    @Autowired LearnerModuleEnrolmentBackfill backfill;
    @Autowired EnrolmentService enrolmentService;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;

    @Test
    @DisplayName("A learner who submitted is enrolled on the module they submitted to")
    void submissionProvesEnrolment() {
        World w = seed();
        Learner learner = learnerRepository.findById(w.learnerId).orElseThrow();
        assertThat(moduleIds(learner)).doesNotContain(w.moduleId);

        backfill.run();

        // Evidence, not inference: they submitted work for it.
        assertThat(moduleIds(learnerRepository.findById(w.learnerId).orElseThrow()))
                .contains(w.moduleId);
    }

    @Test
    @DisplayName("A learner with no submissions is still enrolled on their learnership's modules")
    void learnershipImpliesEnrolment() {
        World w = seed();
        submissionRepository.deleteAll();

        backfill.run();

        // The same rule registration applies. Writing it down makes the join table agree with
        // what the application already asserts rather than adding a third opinion.
        assertThat(moduleIds(learnerRepository.findById(w.learnerId).orElseThrow()))
                .contains(w.moduleId);
    }

    @Test
    @DisplayName("Running it twice changes nothing the second time")
    void isIdempotent() {
        World w = seed();

        backfill.run();
        Set<Long> afterFirst = moduleIds(learnerRepository.findById(w.learnerId).orElseThrow());
        backfill.run();
        Set<Long> afterSecond = moduleIds(learnerRepository.findById(w.learnerId).orElseThrow());

        assertThat(afterSecond).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("It never removes an enrolment somebody set deliberately")
    void neverRemoves() {
        World w = seed();
        // A module on another learnership, enrolled by hand — an elective, a transfer, whatever
        // reason a human had. Not this task's to revoke.
        Learnership other = learnershipRepository.save(Learnership.builder().name("Other " + System.nanoTime()).build());
        Category otherCategory = categoryRepository.save(
                Category.builder().categoryType("ELECTIVE").learnership(other).build());
        Module elective = moduleRepository.save(Module.builder()
                .moduleName("Elective").moduleCode("EL" + System.nanoTime()).category(otherCategory).build());

        Learner learner = learnerRepository.findById(w.learnerId).orElseThrow();
        learner.setModules(new HashSet<>(Set.of(elective)));
        learnerRepository.save(learner);

        backfill.run();

        assertThat(moduleIds(learnerRepository.findById(w.learnerId).orElseThrow()))
                .contains(elective.getId(), w.moduleId);
    }

    @Test
    @DisplayName("A module created after a learner registered enrols them")
    void moduleCreatedLaterEnrolsExistingLearners() {
        World w = seed();

        // The actual cause of the production gap: registration enrols against the modules that
        // exist at the time, and nothing went back when a module was added later.
        Module addedLater = moduleRepository.save(Module.builder()
                .moduleName("Added Later")
                .moduleCode("LATE" + System.nanoTime())
                .category(categoryRepository.findById(w.categoryId).orElseThrow())
                .build());

        int enrolled = enrolmentService.enrolExistingLearnersOn(addedLater);

        assertThat(enrolled).isEqualTo(1);
        assertThat(moduleIds(learnerRepository.findById(w.learnerId).orElseThrow()))
                .contains(addedLater.getId());
    }

    @Test
    @DisplayName("Enrolling on a module twice is not an error and adds nothing")
    void ensureEnrolledIsIdempotent() {
        World w = seed();
        Learner learner = learnerRepository.findById(w.learnerId).orElseThrow();
        Module module = moduleRepository.findById(w.moduleId).orElseThrow();

        enrolmentService.ensureEnrolled(learner, module);
        int after = moduleIds(learnerRepository.findById(w.learnerId).orElseThrow()).size();
        enrolmentService.ensureEnrolled(learner, module);

        assertThat(moduleIds(learnerRepository.findById(w.learnerId).orElseThrow())).hasSize(after);
    }

    @Test
    @DisplayName("A module with no learnership has no roster to derive, and does not blow up")
    void moduleWithoutLearnershipIsSkipped() {
        Module orphan = moduleRepository.save(Module.builder()
                .moduleName("Orphan").moduleCode("ORP" + System.nanoTime()).build());

        assertThat(enrolmentService.enrolExistingLearnersOn(orphan)).isZero();
    }

    private Set<Long> moduleIds(Learner learner) {
        Set<Long> ids = new HashSet<>();
        if (learner.getModules() != null) {
            learner.getModules().forEach(m -> ids.add(m.getId()));
        }
        return ids;
    }

    private record World(Long learnerId, Long moduleId, Long categoryId) {}

    private World seed() {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(
                Learnership.builder().name("Enrol " + unique).build());
        Category category = categoryRepository.save(
                Category.builder().categoryType("CORE").learnership(learnership).build());
        Module module = moduleRepository.save(Module.builder()
                .moduleName("Enrol Module " + unique)
                .moduleCode("EM" + unique)
                .category(category)
                .build());

        // Registered without any modules — the state the production data is in.
        Learner learner = learnerRepository.save(Learner.builder()
                .fullName("Unenrolled Learner")
                .learnerCode("ENR" + unique)
                .email("enr" + unique + "@example.com")
                .phoneNumber("0700000000")
                .learnership(learnership)
                .modules(new HashSet<>())
                .build());

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Task").description("").dueDate(LocalDateTime.now().plusDays(7)).module(module).build());
        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Session").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).build());
        submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath("lms_secure/x").originalFilename("essay.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());

        return new World(learner.getId(), module.getId(), category.getId());
    }
}
