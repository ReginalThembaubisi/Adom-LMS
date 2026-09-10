package com.example.learnerassignments.service;

import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.ModuleRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills in the learner_modules rows that were never written.
 *
 * Registration enrols a learner on the modules that exist at that moment. Modules created
 * afterwards never enrolled the learners already registered, so a cohort that signed up before
 * its modules were set up has no rows at all. Measured in production: 114 of 299 submissions
 * belonged to learners with no row for the module they submitted to.
 *
 * Two things establish enrolment, and both are used:
 *
 * <ul>
 *   <li><strong>A submission.</strong> Somebody who submitted work for a module was on it.
 *       This is evidence, not inference.</li>
 *   <li><strong>The learnership.</strong> A learner on a learnership is expected to complete
 *       its modules — that is exactly the rule registration applies when it enrols them, and
 *       the rule the portal applies when deciding what they can see. Writing it down makes the
 *       join table agree with what the application already asserts, rather than inventing a
 *       third opinion.</li>
 * </ul>
 *
 * Idempotent: it only ever adds a missing pair, so a second run finds nothing to do. It never
 * removes a row — an enrolment somebody set deliberately is not this task's to revoke.
 */
@Component
@Order(40)
@RequiredArgsConstructor
@Slf4j
public class LearnerModuleEnrolmentBackfill implements CommandLineRunner {

    private final LearnerRepository learnerRepository;
    private final ModuleRepository moduleRepository;
    private final SubmissionRepository submissionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Learner> learners = learnerRepository.findAll();

        Map<Long, Set<Long>> provenBySubmission = new HashMap<>();
        for (Object[] pair : submissionRepository.findEnrolmentPairsProvenBySubmissions()) {
            provenBySubmission
                    .computeIfAbsent((Long) pair[0], k -> new HashSet<>())
                    .add((Long) pair[1]);
        }

        Map<Long, List<Module>> modulesByLearnership = new HashMap<>();
        Map<Long, Module> modulesById = new HashMap<>();
        for (Module module : moduleRepository.findAll()) {
            modulesById.put(module.getId(), module);
        }

        // Work out everything that is missing before writing any of it, so the intent can be
        // logged as a whole. A line that appears only after the change has already happened is
        // no use to whoever has to decide whether to let it happen.
        Map<Learner, Set<Module>> toAdd = new HashMap<>();
        int fromSubmissions = 0;
        int fromLearnership = 0;

        for (Learner learner : learners) {
            Set<Long> existing = new HashSet<>();
            if (learner.getModules() != null) {
                learner.getModules().forEach(m -> existing.add(m.getId()));
            }

            Set<Module> missing = new HashSet<>();

            for (Long moduleId : provenBySubmission.getOrDefault(learner.getId(), Set.of())) {
                Module module = modulesById.get(moduleId);
                if (module != null && !existing.contains(moduleId)) {
                    missing.add(module);
                    fromSubmissions++;
                }
            }

            Long learnershipId = learner.getLearnership() != null ? learner.getLearnership().getId() : null;
            if (learnershipId != null) {
                List<Module> learnershipModules = modulesByLearnership.computeIfAbsent(
                        learnershipId, moduleRepository::findByCategoryLearnershipId);
                for (Module module : learnershipModules) {
                    if (!existing.contains(module.getId()) && missing.add(module)) {
                        fromLearnership++;
                    }
                }
            }

            if (!missing.isEmpty()) {
                toAdd.put(learner, missing);
            }
        }

        int pairs = toAdd.values().stream().mapToInt(Set::size).sum();

        if (pairs > 0) {
            log.warn("About to add {} learner-module enrolment row(s) across {} learner(s): {} "
                    + "proven by an existing submission, {} implied by the learner's learnership. "
                    + "This changes data, not just code. Nothing is removed, and a second run "
                    + "finds nothing to do.", pairs, toAdd.size(), fromSubmissions, fromLearnership);
            toAdd.forEach((learner, modules) -> {
                List<String> codes = new ArrayList<>();
                modules.forEach(m -> codes.add(m.getModuleCode() == null ? String.valueOf(m.getId()) : m.getModuleCode()));
                log.info("Enrolling {} on {} module(s): {}", learner.getLearnerCode(), modules.size(), codes);
            });

            toAdd.forEach((learner, modules) -> {
                if (learner.getModules() == null) {
                    learner.setModules(new HashSet<>());
                }
                learner.getModules().addAll(modules);
                learnerRepository.save(learner);
            });
        }

        // Always logged, no-op included. Finding nothing and never running look identical in a
        // log that only speaks up on a change, and "never ran" is the realistic failure here.
        log.info("Learner module enrolment backfill complete: scanned {} learner(s), added {} "
                + "enrolment row(s) across {} learner(s) ({} proven by submissions, {} from "
                + "learnership membership).",
                learners.size(), pairs, toAdd.size(), fromSubmissions, fromLearnership);
    }
}
