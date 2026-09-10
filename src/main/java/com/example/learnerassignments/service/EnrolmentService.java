package com.example.learnerassignments.service;

import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * Keeps the learner_modules join table in step with what the rest of the system already
 * believes about who is on which module.
 *
 * Registration enrols a learner on the modules that exist at the moment they register. Modules
 * are created afterwards, by admins and lecturers, and nothing went back to enrol the learners
 * already registered — so every module created after a cohort signed up left that cohort off
 * its roster. That is why 114 of 299 submissions in production belonged to learners with no
 * row for the module they submitted to.
 *
 * Visibility no longer depends on this table (the grading console reads submissions directly),
 * but "who has not submitted yet" does, and so will Phase 7's completeness dashboard. A roster
 * that silently omits people cannot answer that question.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrolmentService {

    private final LearnerRepository learnerRepository;
    private final ModuleRepository moduleRepository;

    /**
     * Enrols every learner on the module's learnership onto a newly created module.
     *
     * Called when a module is created, which is the moment the gap used to open. Silent when
     * the module has no learnership — an unattached module has no roster to derive.
     */
    @Transactional
    public int enrolExistingLearnersOn(Module module) {
        Long learnershipId = learnershipIdOf(module);
        if (learnershipId == null) {
            return 0;
        }
        List<Learner> learners = learnerRepository.findByLearnership_Id(learnershipId);
        int added = 0;
        for (Learner learner : learners) {
            if (addModule(learner, module)) {
                added++;
            }
        }
        if (added > 0) {
            log.info("Enrolled {} existing learner(s) on new module {} ({}).",
                    added, module.getModuleCode(), module.getModuleName());
        }
        return added;
    }

    /**
     * Makes sure a learner is on a module, used where the system has just accepted proof that
     * they are — a submission. A safety net rather than the main path: if enrolment is somehow
     * missed at module creation, accepting work is the last honest moment to record it.
     */
    @Transactional
    public void ensureEnrolled(Learner learner, Module module) {
        if (learner == null || module == null) {
            return;
        }
        if (addModule(learner, module)) {
            log.info("Enrolled learner {} on module {} on submission — the roster did not have them.",
                    learner.getLearnerCode(), module.getModuleCode());
        }
    }

    /** True if the module was actually added, so callers can count real changes. */
    private boolean addModule(Learner learner, Module module) {
        if (learner.getModules() == null) {
            learner.setModules(new HashSet<>());
        }
        boolean alreadyThere = learner.getModules().stream()
                .anyMatch(m -> m.getId() != null && m.getId().equals(module.getId()));
        if (alreadyThere) {
            return false;
        }
        learner.getModules().add(module);
        learnerRepository.save(learner);
        return true;
    }

    private Long learnershipIdOf(Module module) {
        return module.getCategory() != null && module.getCategory().getLearnership() != null
                ? module.getCategory().getLearnership().getId()
                : null;
    }
}
