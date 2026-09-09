package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.security.StaffPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scoping, decided from an identity passed in rather than from the security context.
 *
 * The first test is the one the brief asks for first, and it is not an edge case: an assessor
 * with no assignment rows is every assessor between being created and being assigned, which
 * is the state most of them are in when they first sign in. A permissive default there would
 * be indistinguishable from the unscoped access this phase exists to remove.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScopeServiceTest {

    @Autowired ScopeService scopeService;
    @Autowired AssessorRepository assessorRepository;
    @Autowired ModeratorRepository moderatorRepository;
    @Autowired AssessorAssignmentRepository assessorAssignmentRepository;
    @Autowired ModeratorAssignmentRepository moderatorAssignmentRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired LecturerRepository lecturerRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Assessor assessor;
    private Moderator moderator;
    private Learner learnerA;
    private Learner learnerB;
    private Learner learnerC;
    private Learnership learnership;
    private Learnership otherLearnership;
    private Category coreCategory;
    private Module coreModule;

    @BeforeEach
    void seed() {
        learnership = learnershipRepository.save(Learnership.builder()
                .name("MICT SETA Systems Development").createdAt(LocalDateTime.now()).build());
        otherLearnership = learnershipRepository.save(Learnership.builder()
                .name("Unrelated Learnership").createdAt(LocalDateTime.now()).build());

        Lecturer lecturer = lecturerRepository.save(Lecturer.builder()
                .fullName("Prof. Owner").username("owner-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("x")).createdAt(LocalDateTime.now()).build());

        coreCategory = categoryRepository.save(Category.builder()
                .categoryType("CORE").learnership(learnership).lecturer(lecturer).build());

        coreModule = moduleRepository.save(Module.builder()
                .moduleName("Systems Analysis").moduleCode("SA101")
                .category(coreCategory).createdAt(LocalDateTime.now()).build());

        learnerA = createLearner("202600001", "Amanda", learnership, "2026-01", coreModule);
        learnerB = createLearner("202600002", "Bongani", learnership, "2026-01", coreModule);
        learnerC = createLearner("202600003", "Charmaine", learnership, "2026-02", coreModule);

        assessor = assessorRepository.save(Assessor.builder()
                .fullName("An Assessor").username("assessor-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("x")).createdAt(LocalDateTime.now()).build());
        moderator = moderatorRepository.save(Moderator.builder()
                .fullName("A Moderator").username("moderator-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("x")).createdAt(LocalDateTime.now()).build());
    }

    private Learner createLearner(String code, String name, Learnership ls, String cohort, Module module) {
        Set<Module> modules = new HashSet<>();
        modules.add(module);
        return learnerRepository.save(Learner.builder()
                .learnerCode(code).fullName(name).learnership(ls).cohort(cohort)
                .passwordHash(passwordEncoder.encode("x")).modules(modules)
                .createdAt(LocalDateTime.now()).build());
    }

    private StaffPrincipal asAssessor() {
        return StaffPrincipal.assessor(assessor.getId(), assessor.getUsername());
    }

    private StaffPrincipal asModerator() {
        return StaffPrincipal.moderator(moderator.getId(), moderator.getUsername());
    }

    // --- The default, which is the whole point ---

    @Test
    @DisplayName("an assessor with no assignments reaches nothing at all")
    void unassignedAssessorReachesNothing() {
        assertThat(assessorAssignmentRepository.countByAssessor_Id(assessor.getId())).isZero();

        assertThat(scopeService.accessibleLearnerIds(asAssessor()))
                .as("no assignment rows must mean no learners, never all learners")
                .isEmpty();
        assertThat(scopeService.canAccessLearner(asAssessor(), learnerA.getId())).isFalse();
        assertThat(scopeService.accessibleModules(asAssessor())).isEmpty();
        assertThat(scopeService.accessibleSessions(asAssessor())).isEmpty();
        assertThat(scopeService.canAccessModule(asAssessor(), coreModule.getId())).isFalse();
    }

    @Test
    @DisplayName("a moderator with no assignments reaches nothing at all")
    void unassignedModeratorReachesNothing() {
        assertThat(moderatorAssignmentRepository.countByModerator_Id(moderator.getId())).isZero();

        assertThat(scopeService.accessibleLearnerIds(asModerator())).isEmpty();
        assertThat(scopeService.canAccessLearner(asModerator(), learnerA.getId())).isFalse();
        assertThat(scopeService.accessibleModules(asModerator())).isEmpty();
    }

    @Test
    @DisplayName("a null identity reaches nothing rather than throwing")
    void nullPrincipalReachesNothing() {
        assertThat(scopeService.accessibleLearnerIds(null)).isEmpty();
        assertThat(scopeService.canAccessLearner(null, learnerA.getId())).isFalse();
        assertThat(scopeService.canAccessModule(null, coreModule.getId())).isFalse();
        assertThat(scopeService.accessibleModules(null)).isEmpty();
    }

    // --- Assessors ---

    @Test
    @DisplayName("an assessor assigned to two learners cannot reach a third")
    void assessorSeesOnlyAssignedLearners() {
        assessorAssignmentRepository.save(AssessorAssignment.builder()
                .assessor(assessor).learner(learnerA).build());
        assessorAssignmentRepository.save(AssessorAssignment.builder()
                .assessor(assessor).learner(learnerB).build());

        assertThat(scopeService.accessibleLearnerIds(asAssessor()))
                .containsExactlyInAnyOrder(learnerA.getId(), learnerB.getId());
        assertThat(scopeService.canAccessLearner(asAssessor(), learnerC.getId()))
                .as("learner C was never assigned")
                .isFalse();
    }

    @Test
    @DisplayName("an assessor assigned to a category reaches its enrolled learners")
    void assessorByCategory() {
        assessorAssignmentRepository.save(AssessorAssignment.builder()
                .assessor(assessor).category(coreCategory).build());

        assertThat(scopeService.accessibleLearnerIds(asAssessor()))
                .containsExactlyInAnyOrder(learnerA.getId(), learnerB.getId(), learnerC.getId());
        assertThat(scopeService.accessibleModules(asAssessor()))
                .extracting(Module::getId).containsExactly(coreModule.getId());
    }

    // --- Moderators ---

    @Test
    @DisplayName("a moderator assigned to a cohort sees no learner outside it")
    void moderatorByCohort() {
        moderatorAssignmentRepository.save(ModeratorAssignment.builder()
                .moderator(moderator).learnership(learnership).cohort("2026-01")
                .scope(ModerationScope.FULL).build());

        assertThat(scopeService.accessibleLearnerIds(asModerator()))
                .containsExactlyInAnyOrder(learnerA.getId(), learnerB.getId());
        assertThat(scopeService.canAccessLearner(asModerator(), learnerC.getId()))
                .as("learner C is on cohort 2026-02")
                .isFalse();
    }

    @Test
    @DisplayName("a moderator assigned to a learnership with no cohort sees all of its cohorts")
    void moderatorWholeLearnership() {
        moderatorAssignmentRepository.save(ModeratorAssignment.builder()
                .moderator(moderator).learnership(learnership).build());

        assertThat(scopeService.accessibleLearnerIds(asModerator()))
                .containsExactlyInAnyOrder(learnerA.getId(), learnerB.getId(), learnerC.getId());
    }

    @Test
    @DisplayName("a moderator assigned elsewhere sees none of this learnership")
    void moderatorOnAnotherLearnership() {
        moderatorAssignmentRepository.save(ModeratorAssignment.builder()
                .moderator(moderator).learnership(otherLearnership).build());

        assertThat(scopeService.accessibleLearnerIds(asModerator())).isEmpty();
    }

    // --- Admin ---

    @Test
    @DisplayName("an admin is unaffected by scoping")
    void adminIsUnscoped() {
        StaffPrincipal admin = StaffPrincipal.admin(1L, "admin");

        assertThat(scopeService.canAccessLearner(admin, learnerA.getId())).isTrue();
        assertThat(scopeService.canAccessLearner(admin, learnerC.getId())).isTrue();
        assertThat(scopeService.canAccessModule(admin, coreModule.getId())).isTrue();
    }

    // --- The shape constraint ---

    @Test
    @DisplayName("scoping resolves with nothing in the security context")
    void doesNotDependOnTheSecurityContext() {
        assessorAssignmentRepository.save(AssessorAssignment.builder()
                .assessor(assessor).learner(learnerA).build());

        // Phase 8's exporter runs on an @Async worker, where no authentication is bound to
        // the thread. An implementation that reached into SecurityContextHolder would satisfy
        // every other test in this class and fail there. Clearing the context asserts the
        // constraint directly; running it on another thread would instead have tested
        // transaction visibility, which is the caller's concern and not this one's.
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        assertThat(scopeService.canAccessLearner(asAssessor(), learnerA.getId()))
                .as("the identity is a parameter, so an empty context changes nothing")
                .isTrue();
        assertThat(scopeService.canAccessLearner(asAssessor(), learnerC.getId())).isFalse();
        assertThat(scopeService.accessibleLearnerIds(asAssessor()))
                .containsExactly(learnerA.getId());
    }
}
