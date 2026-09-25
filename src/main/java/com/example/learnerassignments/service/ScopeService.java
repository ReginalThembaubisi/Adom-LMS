package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.security.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a given staff identity may reach.
 *
 * Two rules hold throughout, and both are the point of the phase:
 *
 *  - **The identity is a parameter.** Nothing here reads SecurityContextHolder, so the same
 *    decisions are available to the Phase 8 exporter on an @Async worker with no request
 *    bound to the thread.
 *  - **Absence of a grant is absence of access.** An assessor or moderator with no assignment
 *    rows reaches nothing. That is the state of every account between being created and being
 *    assigned, which is most of them on the day they first sign in — and a permissive default
 *    there is indistinguishable from the bug this exists to fix.
 *
 * Admins are unscoped. Lecturers resolve through Module -> Category -> Lecturer, the pattern
 * SubmissionController already used.
 */
@Service
@RequiredArgsConstructor
public class ScopeService {

    private final AssessorAssignmentRepository assessorAssignmentRepository;
    private final ModeratorAssignmentRepository moderatorAssignmentRepository;
    private final ModuleRepository moduleRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final LearnerRepository learnerRepository;

    // --- Learners ---

    /** The learners this identity may reach. Empty is a real answer, not a missing filter. */
    @Transactional(readOnly = true)
    public Set<Long> accessibleLearnerIds(StaffPrincipal principal) {
        if (principal == null) {
            return Collections.emptySet();
        }
        return switch (principal.role()) {
            case ADMIN -> new HashSet<>(learnerRepository.findAllIds());
            case LECTURER -> lecturerLearnerIds(principal.id());
            case ASSESSOR -> new HashSet<>(assessorAssignmentRepository.findAccessibleLearnerIds(principal.id()));
            case MODERATOR -> new HashSet<>(moderatorAssignmentRepository.findAccessibleLearnerIds(principal.id()));
        };
    }

    @Transactional(readOnly = true)
    public boolean canAccessLearner(StaffPrincipal principal, Long learnerId) {
        if (principal == null || learnerId == null) {
            return false;
        }
        if (principal.isAdmin()) {
            return true;
        }
        return accessibleLearnerIds(principal).contains(learnerId);
    }

    // --- Submissions ---

    /**
     * Whether this identity may read one submission.
     *
     * A lecturer is asked about the module rather than the learner: they own a module, not a
     * roster, and a learner enrolled on it may have work on other modules that is none of
     * their business.
     */
    @Transactional(readOnly = true)
    public boolean canAccessSubmission(StaffPrincipal principal, Submission submission) {
        if (principal == null || submission == null) {
            return false;
        }
        if (principal.isAdmin()) {
            return true;
        }
        if (principal.role() == StaffPrincipal.StaffRole.LECTURER) {
            return ownsModuleOf(principal, submission);
        }
        return submission.getLearner() != null
                && canAccessLearner(principal, submission.getLearner().getId());
    }

    // --- Modules and sessions ---

    /** The modules this identity may see. */
    @Transactional(readOnly = true)
    public List<Module> accessibleModules(StaffPrincipal principal) {
        if (principal == null) {
            return Collections.emptyList();
        }
        if (principal.isAdmin()) {
            return moduleRepository.findAll();
        }
        if (principal.role() == StaffPrincipal.StaffRole.LECTURER) {
            return moduleRepository.findByCategoryLecturerId(principal.id());
        }

        // An assessor or moderator sees the modules their assigned learners are enrolled on,
        // and nothing else. No assignments means no learners, which means no modules.
        Set<Long> learnerIds = accessibleLearnerIds(principal);
        if (learnerIds.isEmpty()) {
            return Collections.emptyList();
        }
        return moduleRepository.findByLearnerIdIn(learnerIds);
    }

    @Transactional(readOnly = true)
    public boolean canAccessModule(StaffPrincipal principal, Long moduleId) {
        if (principal == null || moduleId == null) {
            return false;
        }
        if (principal.isAdmin()) {
            return true;
        }
        if (principal.role() == StaffPrincipal.StaffRole.LECTURER) {
            return moduleRepository.findById(moduleId)
                    .map(m -> lecturerOwns(principal.id(), m))
                    .orElse(false);
        }
        return accessibleModules(principal).stream().anyMatch(m -> m.getId().equals(moduleId));
    }

    /** The submission sessions this identity may see, derived from its modules. */
    @Transactional(readOnly = true)
    public List<SubmissionSession> accessibleSessions(StaffPrincipal principal) {
        if (principal == null) {
            return Collections.emptyList();
        }
        if (principal.isAdmin()) {
            return sessionRepository.findAllByOrderByCreatedAtDesc();
        }
        Set<Long> moduleIds = accessibleModules(principal).stream()
                .map(Module::getId).collect(Collectors.toSet());
        if (moduleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sessionRepository.findByAssignmentModuleIdInOrderByCreatedAtDesc(moduleIds);
    }

    @Transactional(readOnly = true)
    public boolean canAccessSession(StaffPrincipal principal, Long sessionId) {
        if (principal == null || sessionId == null) {
            return false;
        }
        if (principal.isAdmin()) {
            return true;
        }
        // One session and one module check, rather than listing every session this identity
        // can see. Deleted sessions stay unreachable, as they are from the listing.
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getDeletedAt() == null)
                .map(s -> s.getAssignment() == null ? null : s.getAssignment().getModule())
                .map(m -> canAccessModule(principal, m.getId()))
                .orElse(false);
    }

    // --- Lecturer resolution, as SubmissionController already did it ---

    private Set<Long> lecturerLearnerIds(Long lecturerId) {
        List<Long> moduleIds = moduleRepository.findByCategoryLecturerId(lecturerId).stream()
                .map(Module::getId)
                .collect(Collectors.toList());
        if (moduleIds.isEmpty()) {
            return Collections.emptySet();
        }
        // Through the join table, for the same reason as accessibleModules.
        return new HashSet<>(learnerRepository.findIdsByModuleIdIn(moduleIds));
    }

    private boolean lecturerOwns(Long lecturerId, Module module) {
        return module.getCategory() != null
                && module.getCategory().getLecturer() != null
                && module.getCategory().getLecturer().getId().equals(lecturerId);
    }

    private boolean ownsModuleOf(StaffPrincipal principal, Submission submission) {
        return submission.getSession() != null
                && submission.getSession().getAssignment() != null
                && submission.getSession().getAssignment().getModule() != null
                && lecturerOwns(principal.id(), submission.getSession().getAssignment().getModule());
    }
}
