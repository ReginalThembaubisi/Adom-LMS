package com.example.learnerassignments.service;

import com.example.learnerassignments.model.Learnership;
import com.example.learnerassignments.model.PoeDocumentRequirement;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.repository.LearnershipRepository;
import com.example.learnerassignments.repository.PoeDocumentRequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gives every learnership a starting set of document requirements it can then edit.
 *
 * Until now the required set lived in the {@link PoeDocumentType} enum and applied to everybody.
 * That set is a reasonable default — it is what the provider has been collecting — so each
 * learnership is seeded with it rather than starting empty and reporting every learner complete.
 *
 * <p><strong>The date matters more than the rows.</strong> {@code required_from} is stamped with
 * the day the learnership is first seeded, which for existing learnerships is the day this ships.
 * Everyone already registered therefore sits before their requirements took effect, and the
 * dashboard reports them exempt rather than missing four documents each. That is the difference
 * between a screen a facilitator uses and a wall of red they learn to ignore. Exempt is not green:
 * it is counted and labelled separately, a learner whose checklist is entirely exempt is never
 * reported complete, and an admin who wants the existing cohort chased moves the date back — once
 * the cohort has actually been asked.
 *
 * <p>Idempotent: it only inserts the (learnership, type) pairs that have no row. A learnership
 * somebody has already configured is left exactly as configured — including a requirement they
 * switched off, which this must never switch back on.
 */
@Component
@Order(60)
@RequiredArgsConstructor
@Slf4j
public class PoeRequirementSeed implements CommandLineRunner {

    private final LearnershipRepository learnershipRepository;
    private final PoeDocumentRequirementRepository requirementRepository;
    private final PoeRequirementService requirementService;

    @Override
    public void run(String... args) {
        List<Learnership> learnerships = learnershipRepository.findAll();
        LocalDate effectiveFrom = LocalDate.now();

        Set<String> existing = new HashSet<>();
        for (PoeDocumentRequirement requirement : requirementRepository.findAll()) {
            Long learnershipId = requirement.getLearnership() == null
                    ? null : requirement.getLearnership().getId();
            existing.add(learnershipId + ":" + requirement.getDocumentType());
        }

        // Work out the whole insert before writing any of it, so the intent can be logged as one
        // statement rather than reconstructed afterwards from the rows.
        List<Learnership> toSeed = new ArrayList<>();
        int rowsPlanned = 0;
        for (Learnership learnership : learnerships) {
            List<String> missing = new ArrayList<>();
            for (PoeDocumentType type : PoeDocumentType.required()) {
                if (!existing.contains(learnership.getId() + ":" + type)) {
                    missing.add(type.name());
                }
            }
            if (!missing.isEmpty()) {
                toSeed.add(learnership);
                rowsPlanned += missing.size();
                log.info("Will seed learnership {} ({}) with required documents: {}",
                        learnership.getId(), learnership.getName(), missing);
            }
        }

        if (rowsPlanned > 0) {
            log.warn("About to insert {} PoE document requirement row(s) across {} learnership(s), "
                    + "each with required_from={}. This changes data, not just code. Learners "
                    + "registered before that date are reported as exempt, not missing. Nothing "
                    + "existing is modified or removed, and a second run finds nothing to do.",
                    rowsPlanned, toSeed.size(), effectiveFrom);
        }

        int inserted = 0;
        for (Learnership learnership : toSeed) {
            inserted += requirementService.seedDefaults(learnership, effectiveFrom);
        }

        // Always logged, no-op included. A log that only speaks up on a change makes "found
        // nothing to do" and "never ran" look identical, and never ran is the failure that would
        // leave the dashboard silently reporting every learner complete.
        log.info("PoE requirement seed complete: {} learnership(s) scanned, {} requirement row(s) "
                + "inserted across {} learnership(s), {} row(s) already configured and left alone.",
                learnerships.size(), inserted, toSeed.size(), existing.size());
    }
}
