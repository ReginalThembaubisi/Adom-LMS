package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.OpeningDtos.OpeningDto;
import com.example.learnerassignments.dto.OpeningDtos.OpeningRequest;
import com.example.learnerassignments.dto.OpeningDtos.PublicOpeningDto;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.ApplicationStatus;
import com.example.learnerassignments.model.JobOpening;
import com.example.learnerassignments.model.LearnershipStatus;
import com.example.learnerassignments.model.OpeningCategory;
import com.example.learnerassignments.repository.JobOpeningRepository;
import com.example.learnerassignments.repository.LearnershipApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Job and internship openings the admin posts for the website's Careers page. */
@Service
@RequiredArgsConstructor
public class OpeningService {

    private final JobOpeningRepository openingRepository;
    private final LearnershipApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public List<OpeningDto> listForAdmin() {
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : applicationRepository.countByOpeningAndStatus()) {
            counts.computeIfAbsent((Long) row[0], k -> new LinkedHashMap<>())
                    .put(((ApplicationStatus) row[1]).name(), (Long) row[2]);
        }
        LocalDate today = LearnershipService.today();
        return openingRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(o -> toAdminDto(o, counts.getOrDefault(o.getId(), Map.of()), today))
                .toList();
    }

    @Transactional
    public OpeningDto create(OpeningRequest request) {
        JobOpening opening = new JobOpening();
        opening.setStatus(LearnershipStatus.DRAFT);
        apply(opening, request);
        opening.setCreatedAt(LocalDateTime.now());
        return toAdminDto(openingRepository.save(opening), Map.of(), LearnershipService.today());
    }

    @Transactional
    public OpeningDto update(Long id, OpeningRequest request) {
        JobOpening opening = require(id);
        apply(opening, request);
        opening.setUpdatedAt(LocalDateTime.now());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : applicationRepository.countByOpeningAndStatus()) {
            if (id.equals(row[0])) counts.put(((ApplicationStatus) row[1]).name(), (Long) row[2]);
        }
        return toAdminDto(opening, counts, LearnershipService.today());
    }

    /** Only an opening nobody has applied to; one with applications is closed instead. */
    @Transactional
    public void delete(Long id) {
        JobOpening opening = require(id);
        if (applicationRepository.countByOpening_Id(id) > 0) {
            throw new IllegalArgumentException("This opening already has applications, so it can't be deleted. Set it to Closed instead.");
        }
        openingRepository.delete(opening);
    }

    /** Openings taking applications, soonest closing first, open-until-filled last. */
    @Transactional(readOnly = true)
    public List<PublicOpeningDto> openOpenings() {
        LocalDate today = LearnershipService.today();
        return openingRepository.findByStatus(LearnershipStatus.OPEN).stream()
                .filter(o -> o.isAcceptingApplications(today))
                .sorted(Comparator.comparing(JobOpening::getClosingDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(JobOpening::getTitle))
                .map(OpeningService::toPublicDto)
                .toList();
    }

    public JobOpening require(Long id) {
        return openingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Opening not found: " + id));
    }

    private static void apply(JobOpening o, OpeningRequest r) {
        o.setTitle(r.getTitle().trim());
        o.setCategory(parseCategory(r.getCategory()));
        o.setDivision(blankToNull(r.getDivision()));
        o.setLocation(blankToNull(r.getLocation()));
        o.setPositions(r.getPositions());
        o.setClosingDate(r.getClosingDate());
        o.setDescription(blankToNull(r.getDescription()));
        o.setRequirements(blankToNull(r.getRequirements()));
        if (r.getStatus() != null && !r.getStatus().isBlank()) {
            try {
                o.setStatus(LearnershipStatus.valueOf(r.getStatus().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Status must be DRAFT, OPEN or CLOSED.");
            }
        }
        if (o.getStatus() == LearnershipStatus.OPEN && o.getClosingDate() != null
                && o.getClosingDate().isBefore(LearnershipService.today())) {
            throw new IllegalArgumentException("The closing date has already passed. Move it forward or set the opening to Closed.");
        }
    }

    public static OpeningCategory parseCategory(String raw) {
        try {
            return OpeningCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown category: " + raw);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static OpeningDto toAdminDto(JobOpening o, Map<String, Long> counts, LocalDate today) {
        return OpeningDto.builder()
                .id(o.getId())
                .title(o.getTitle())
                .category(o.getCategory().name())
                .categoryLabel(o.getCategory().getLabel())
                .division(o.getDivision())
                .location(o.getLocation())
                .positions(o.getPositions())
                .closingDate(o.getClosingDate())
                .description(o.getDescription())
                .requirements(o.getRequirements())
                .status(o.getStatus().name())
                .acceptingApplications(o.isAcceptingApplications(today))
                .applicationCounts(counts)
                .applicationTotal(counts.values().stream().mapToLong(Long::longValue).sum())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private static PublicOpeningDto toPublicDto(JobOpening o) {
        List<String> requirements = o.getRequirements() == null ? List.of()
                : Arrays.stream(o.getRequirements().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return PublicOpeningDto.builder()
                .id(o.getId())
                .title(o.getTitle())
                .category(o.getCategory().name())
                .categoryLabel(o.getCategory().getLabel())
                .division(o.getDivision())
                .location(o.getLocation())
                .positions(o.getPositions())
                .closingDate(o.getClosingDate())
                .description(o.getDescription())
                .requirements(requirements)
                .build();
    }
}
