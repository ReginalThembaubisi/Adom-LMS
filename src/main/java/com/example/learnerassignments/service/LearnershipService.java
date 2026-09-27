package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.CreateLearnershipRequest;
import com.example.learnerassignments.dto.LearnershipAdvertDto;
import com.example.learnerassignments.dto.LearnershipResponseDto;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.ApplicationStatus;
import com.example.learnerassignments.model.Learnership;
import com.example.learnerassignments.model.LearnershipStatus;
import com.example.learnerassignments.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Learnerships and their public adverts.
 *
 * A learnership is both the container the LMS organises learners and modules under and, once
 * an admin fills in the advert fields and sets it to Open, the thing the website lists for
 * people to apply to. One record for both, so the learnership an applicant applies to is the
 * same one they are enrolled on when accepted.
 */
@Service
@RequiredArgsConstructor
public class LearnershipService {

    /** Closing dates are South African calendar days, whatever zone the server runs in. */
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final LearnershipRepository learnershipRepository;
    private final LearnershipApplicationRepository applicationRepository;
    private final LearnerRepository learnerRepository;
    private final CategoryRepository categoryRepository;
    private final ModeratorAssignmentRepository moderatorAssignmentRepository;
    private final PoeDocumentRequirementRepository poeDocumentRequirementRepository;
    private final PoeRequirementService poeRequirementService;

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // --- Staff ---

    @Transactional(readOnly = true)
    public List<LearnershipResponseDto> listForAdmin() {
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : applicationRepository.countByLearnershipAndStatus()) {
            counts.computeIfAbsent((Long) row[0], k -> new LinkedHashMap<>())
                    .put(((ApplicationStatus) row[1]).name(), (Long) row[2]);
        }
        LocalDate today = today();
        return learnershipRepository.findAll().stream()
                .sorted(Comparator.comparing(Learnership::getId))
                .map(l -> toAdminDto(l, counts.getOrDefault(l.getId(), Map.of()), today))
                .toList();
    }

    @Transactional(readOnly = true)
    public LearnershipResponseDto getForAdmin(Long id) {
        Learnership learnership = require(id);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : applicationRepository.countByLearnershipAndStatus()) {
            if (id.equals(row[0])) {
                counts.put(((ApplicationStatus) row[1]).name(), (Long) row[2]);
            }
        }
        return toAdminDto(learnership, counts, today());
    }

    @Transactional
    public LearnershipResponseDto create(CreateLearnershipRequest request) {
        Learnership learnership = new Learnership();
        learnership.setStatus(LearnershipStatus.DRAFT);
        apply(learnership, request);
        learnership.setCreatedAt(LocalDateTime.now());
        Learnership saved = learnershipRepository.save(learnership);
        // Seed its document requirements now rather than at the next boot, so there is never a
        // window where a learnership has learners but no requirements and the completeness
        // dashboard reports every one of them complete.
        poeRequirementService.seedDefaults(saved, LocalDate.now());
        return toAdminDto(saved, Map.of(), today());
    }

    @Transactional
    public LearnershipResponseDto update(Long id, CreateLearnershipRequest request) {
        Learnership learnership = require(id);
        apply(learnership, request);
        learnership.setUpdatedAt(LocalDateTime.now());
        return getForAdmin(learnershipRepository.save(learnership).getId());
    }

    /**
     * Deletes a learnership nobody depends on yet — typically an advert created by mistake.
     * One that has learners, categories, moderators or applications is refused: deleting it
     * would cascade into coursework or throw away applicants' records. Close it instead.
     */
    @Transactional
    public void delete(Long id) {
        Learnership learnership = require(id);
        List<String> inUse = new ArrayList<>();
        if (learnerRepository.existsByLearnership_Id(id)) inUse.add("learners");
        if (categoryRepository.existsByLearnership_Id(id)) inUse.add("categories");
        if (moderatorAssignmentRepository.existsByLearnership_Id(id)) inUse.add("moderator assignments");
        if (applicationRepository.countByLearnership_Id(id) > 0) inUse.add("applications");
        if (!inUse.isEmpty()) {
            throw new IllegalArgumentException("This learnership already has " + String.join(", ", inUse)
                    + ", so it can't be deleted. Set its advert to Closed instead.");
        }
        poeDocumentRequirementRepository.deleteAll(poeDocumentRequirementRepository.findByLearnership_Id(id));
        learnershipRepository.delete(learnership);
    }

    // --- Public ---

    /** Adverts currently taking applications, soonest closing first. */
    @Transactional(readOnly = true)
    public List<LearnershipAdvertDto> openAdverts() {
        LocalDate today = today();
        return learnershipRepository.findByStatusOrderByClosingDateAscNameAsc(LearnershipStatus.OPEN).stream()
                .filter(l -> l.isAcceptingApplications(today))
                // Open-until-filled adverts after the dated ones, not before them.
                .sorted(Comparator.comparing(Learnership::getClosingDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toAdvert)
                .toList();
    }

    /** One advert by slug or numeric id — only while it is taking applications. */
    @Transactional(readOnly = true)
    public LearnershipAdvertDto openAdvert(String slugOrId) {
        Learnership learnership = findBySlugOrId(slugOrId)
                .filter(l -> l.isAcceptingApplications(today()))
                .orElseThrow(() -> new ResourceNotFoundException("This learnership is not taking applications."));
        return toAdvert(learnership);
    }

    public Optional<Learnership> findBySlugOrId(String slugOrId) {
        if (slugOrId == null || slugOrId.isBlank()) {
            return Optional.empty();
        }
        Optional<Learnership> bySlug = learnershipRepository.findBySlug(slugOrId.trim().toLowerCase(Locale.ROOT));
        if (bySlug.isPresent()) {
            return bySlug;
        }
        try {
            return learnershipRepository.findById(Long.parseLong(slugOrId.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // --- Mapping ---

    private void apply(Learnership l, CreateLearnershipRequest r) {
        if (r.getName() == null || r.getName().isBlank()) {
            throw new IllegalArgumentException("Learnership name is required.");
        }
        l.setName(r.getName().trim());
        l.setQualificationCode(blankToNull(r.getQualificationCode()));
        if (r.getStatus() != null && !r.getStatus().isBlank()) {
            try {
                l.setStatus(LearnershipStatus.valueOf(r.getStatus().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Status must be DRAFT, OPEN or CLOSED.");
            }
        }
        l.setSeta(blankToNull(r.getSeta()));
        l.setNqfLevel(inRange(r.getNqfLevel(), 1, 10, "NQF level"));
        l.setDurationMonths(inRange(r.getDurationMonths(), 1, 60, "Duration"));
        l.setStipend(inRange(r.getStipend(), 0, 1_000_000, "Stipend"));
        l.setMaxLearners(inRange(r.getMaxLearners(), 1, 100_000, "Number of places"));
        l.setLocation(blankToNull(r.getLocation()));
        l.setIntake(blankToNull(r.getIntake()));
        l.setClosingDate(r.getClosingDate());
        l.setDescription(blankToNull(r.getDescription()));
        l.setRequirements(blankToNull(r.getRequirements()));

        if (l.getStatus() == LearnershipStatus.OPEN && l.getClosingDate() != null
                && l.getClosingDate().isBefore(today())) {
            throw new IllegalArgumentException("The closing date has already passed. Move it forward or set the advert to Closed.");
        }

        String requestedSlug = blankToNull(r.getSlug());
        if (requestedSlug != null) {
            String slug = slugify(requestedSlug);
            if (slug.isEmpty()) {
                throw new IllegalArgumentException("The web address must contain letters or numbers.");
            }
            learnershipRepository.findBySlug(slug)
                    .filter(other -> !other.getId().equals(l.getId()))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("Another learnership already uses the web address \"" + slug + "\".");
                    });
            l.setSlug(slug);
        } else if (l.getSlug() == null) {
            l.setSlug(uniqueSlug(l.getName(), l.getId()));
        }
    }

    private String uniqueSlug(String name, Long selfId) {
        String base = slugify(name);
        if (base.isEmpty()) {
            base = "learnership";
        }
        String candidate = base;
        for (int n = 2; ; n++) {
            Optional<Learnership> taken = learnershipRepository.findBySlug(candidate);
            if (taken.isEmpty() || taken.get().getId().equals(selfId)) {
                return candidate;
            }
            candidate = base + "-" + n;
        }
    }

    static String slugify(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.length() > 150 ? slug.substring(0, 150).replaceAll("-+$", "") : slug;
    }

    private static Integer inRange(Integer value, int min, int max, String field) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Learnership require(Long id) {
        return learnershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Learnership not found: " + id));
    }

    private LearnershipResponseDto toAdminDto(Learnership l, Map<String, Long> counts, LocalDate today) {
        return LearnershipResponseDto.builder()
                .id(l.getId())
                .name(l.getName())
                .qualificationCode(l.getQualificationCode())
                .slug(l.getSlug())
                .status(l.getStatus() != null ? l.getStatus().name() : LearnershipStatus.DRAFT.name())
                .seta(l.getSeta())
                .nqfLevel(l.getNqfLevel())
                .durationMonths(l.getDurationMonths())
                .stipend(l.getStipend())
                .location(l.getLocation())
                .intake(l.getIntake())
                .closingDate(l.getClosingDate())
                .maxLearners(l.getMaxLearners())
                .description(l.getDescription())
                .requirements(l.getRequirements())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .acceptingApplications(l.isAcceptingApplications(today))
                .applicationCounts(counts)
                .applicationTotal(counts.values().stream().mapToLong(Long::longValue).sum())
                .build();
    }

    private LearnershipAdvertDto toAdvert(Learnership l) {
        List<String> requirements = l.getRequirements() == null ? List.of()
                : Arrays.stream(l.getRequirements().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return LearnershipAdvertDto.builder()
                .id(l.getId())
                .slug(l.getSlug())
                .name(l.getName())
                .qualificationCode(l.getQualificationCode())
                .seta(l.getSeta())
                .nqfLevel(l.getNqfLevel())
                .durationMonths(l.getDurationMonths())
                .stipend(l.getStipend())
                .location(l.getLocation())
                .intake(l.getIntake())
                .closingDate(l.getClosingDate())
                .description(l.getDescription())
                .requirements(requirements)
                .build();
    }
}
