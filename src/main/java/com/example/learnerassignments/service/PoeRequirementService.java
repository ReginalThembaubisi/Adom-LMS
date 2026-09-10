package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnershipRequirements;
import com.example.learnerassignments.dto.PoeCompletenessDtos.RequirementRow;
import com.example.learnerassignments.dto.PoeCompletenessDtos.UpdateRequirementRequest;
import com.example.learnerassignments.model.Learnership;
import com.example.learnerassignments.model.PoeDocumentRequirement;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.repository.LearnershipRepository;
import com.example.learnerassignments.repository.PoeDocumentRequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which documents a learnership requires, and from when.
 *
 * The document types stay an enum — that is the vocabulary every learnership shares. Which of
 * them are actually required is per-learnership, so a qualification that never asked for a matric
 * certificate stops showing its whole cohort red for one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoeRequirementService {

    private final PoeDocumentRequirementRepository requirementRepository;
    private final LearnershipRepository learnershipRepository;

    /**
     * Inserts the default requirement rows this learnership is missing, and returns how many.
     *
     * Called both at boot and when a learnership is created, so there is never a window in which a
     * learnership has learners but no requirements. Only ever inserts: a requirement somebody
     * switched off stays off, which is the one thing a re-run must not undo.
     *
     * @param effectiveFrom the date the seeded requirements take effect. Learners who registered
     *                      before it are reported exempt rather than missing.
     */
    @Transactional
    public int seedDefaults(Learnership learnership, LocalDate effectiveFrom) {
        if (learnership == null || learnership.getId() == null) {
            return 0;
        }
        Set<PoeDocumentType> existing = new HashSet<>();
        requirementRepository.findByLearnership_Id(learnership.getId())
                .forEach(r -> existing.add(r.getDocumentType()));

        List<PoeDocumentRequirement> toInsert = new ArrayList<>();
        for (PoeDocumentType type : PoeDocumentType.required()) {
            if (!existing.contains(type)) {
                toInsert.add(PoeDocumentRequirement.builder()
                        .learnership(learnership)
                        .documentType(type)
                        .required(true)
                        .requiredFrom(effectiveFrom)
                        .build());
            }
        }
        if (toInsert.isEmpty()) {
            return 0;
        }
        requirementRepository.saveAll(toInsert);
        return toInsert.size();
    }

    /** Requirements grouped by learnership id, for the dashboard's one read. */
    @Transactional(readOnly = true)
    public Map<Long, List<PoeDocumentRequirement>> requirementsByLearnership(Collection<Long> learnershipIds) {
        Map<Long, List<PoeDocumentRequirement>> byLearnership = new HashMap<>();
        if (learnershipIds == null || learnershipIds.isEmpty()) {
            return byLearnership;
        }
        for (PoeDocumentRequirement requirement : requirementRepository.findByLearnership_IdIn(learnershipIds)) {
            if (requirement.getLearnership() == null || requirement.getDocumentType() == null) {
                continue;
            }
            byLearnership
                    .computeIfAbsent(requirement.getLearnership().getId(), k -> new ArrayList<>())
                    .add(requirement);
        }
        return byLearnership;
    }

    /**
     * What a learnership with no configured rows falls back to.
     *
     * The enum's required set, with no effective date, so a learnership that somehow reached
     * production unseeded over-reports rather than under-reports. Reporting a learner complete
     * when nobody has checked is the failure that reaches a SETA; reporting them incomplete when
     * they are fine is a phone call. The dashboard names any learnership this applies to.
     */
    public List<PoeDocumentRequirement> fallbackRequirements(Learnership learnership) {
        List<PoeDocumentRequirement> fallback = new ArrayList<>();
        for (PoeDocumentType type : PoeDocumentType.required()) {
            fallback.add(PoeDocumentRequirement.builder()
                    .learnership(learnership)
                    .documentType(type)
                    .required(true)
                    .requiredFrom(null)
                    .build());
        }
        return fallback;
    }

    @Transactional(readOnly = true)
    public LearnershipRequirements list(Long learnershipId) {
        Learnership learnership = learnershipRepository.findById(learnershipId).orElse(null);
        if (learnership == null) {
            return null;
        }
        Map<PoeDocumentType, PoeDocumentRequirement> configured = new HashMap<>();
        requirementRepository.findByLearnership_Id(learnershipId)
                .forEach(r -> configured.put(r.getDocumentType(), r));

        // Every type in the vocabulary is listed, configured or not, so the admin can turn one on
        // without first having to know it exists.
        List<RequirementRow> rows = new ArrayList<>();
        for (PoeDocumentType type : PoeDocumentType.values()) {
            PoeDocumentRequirement requirement = configured.get(type);
            rows.add(RequirementRow.builder()
                    .id(requirement == null ? null : requirement.getId())
                    .documentType(type.name())
                    .documentLabel(type.getLabel())
                    .poeSection(type.getPoeSection())
                    .required(requirement != null && requirement.isRequired())
                    .requiredFrom(requirement == null ? null : requirement.getRequiredFrom())
                    .build());
        }
        rows.sort(Comparator.comparingInt(RequirementRow::getPoeSection)
                .thenComparing(RequirementRow::getDocumentLabel));

        return LearnershipRequirements.builder()
                .learnershipId(learnership.getId())
                .learnershipName(learnership.getName())
                .requirements(rows)
                .build();
    }

    /**
     * Turns one requirement on or off, or moves the date it took effect.
     *
     * Moving {@code requiredFrom} back, or clearing it, is how an admin starts counting a cohort
     * that was previously exempt — after the cohort has actually been asked for the documents.
     * It is logged because it changes what the dashboard accuses people of.
     */
    @Transactional
    public LearnershipRequirements update(Long learnershipId, UpdateRequirementRequest request, String actor) {
        Learnership learnership = learnershipRepository.findById(learnershipId).orElse(null);
        if (learnership == null) {
            return null;
        }
        PoeDocumentType type;
        try {
            type = PoeDocumentType.valueOf(request.getDocumentType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown document type: " + request.getDocumentType());
        }

        PoeDocumentRequirement requirement = requirementRepository.findByLearnership_Id(learnershipId).stream()
                .filter(r -> r.getDocumentType() == type)
                .findFirst()
                .orElseGet(() -> PoeDocumentRequirement.builder()
                        .learnership(learnership)
                        .documentType(type)
                        .required(true)
                        .build());

        boolean wasRequired = requirement.isRequired();
        LocalDate wasFrom = requirement.getRequiredFrom();

        if (request.getRequired() != null) {
            requirement.setRequired(request.getRequired());
        }
        if (request.isClearRequiredFrom()) {
            requirement.setRequiredFrom(null);
        } else if (request.getRequiredFrom() != null) {
            requirement.setRequiredFrom(request.getRequiredFrom());
        }
        requirementRepository.save(requirement);

        log.info("PoE requirement changed by {}: learnership {} ({}), {} required {}->{}, "
                        + "required_from {}->{}",
                actor, learnership.getId(), learnership.getName(), type,
                wasRequired, requirement.isRequired(), wasFrom, requirement.getRequiredFrom());

        return list(learnershipId);
    }
}
