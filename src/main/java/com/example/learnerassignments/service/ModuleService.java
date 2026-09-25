package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.ModuleDetailResponseDto;
import com.example.learnerassignments.dto.ModuleFileDto;
import com.example.learnerassignments.dto.ModuleResponseDto;
import com.example.learnerassignments.dto.ModuleSlotDto;
import com.example.learnerassignments.dto.TimelineResponseDto;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.AssignmentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.ModuleFileRepository;
import com.example.learnerassignments.repository.ModuleRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.repository.SubmissionSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final LearnerRepository learnerRepository;
    private final ModuleRepository moduleRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    private final ModuleFileRepository moduleFileRepository;

    @Transactional(readOnly = true)
    public List<ModuleResponseDto> getEnrolledModules(String studentNumber) {
        Learner learner = learnerRepository.findByLearnerCode(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Student number not found: " + studentNumber));

        if (learner.getLearnership() == null) {
            return Collections.emptyList();
        }

        List<Module> modules = moduleRepository.findByCategoryLearnershipId(learner.getLearnership().getId());
        if (modules.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<SubmissionSession>> openSessionsByModule = sessionsForModules(modules).stream()
                .filter(s -> s.getStatus() == SessionStatus.OPEN)
                .collect(Collectors.groupingBy(s -> s.getAssignment().getModule().getId()));
        Set<Long> submittedSessionIds = submittedSessionIds(learner);

        return modules.stream()
                .map(m -> ModuleResponseDto.builder()
                        .id(m.getId())
                        .moduleName(m.getModuleName())
                        .moduleCode(m.getModuleCode())
                        .lecturerName(lecturerName(m))
                        .fileName(fileName(m))
                        .filePath(m.getFilePath())
                        .moduleType(moduleType(m))
                        .files(fileDtos(m))
                        .slots(openSessionsByModule.getOrDefault(m.getId(), List.of()).stream()
                                .map(s -> toSlot(s, submittedSessionIds))
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ModuleDetailResponseDto getModuleDetails(Long moduleId, String studentNumber) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with id: " + moduleId));

        Set<Long> submittedSessionIds = (studentNumber == null || studentNumber.isBlank())
                ? Set.of()
                : learnerRepository.findByLearnerCode(studentNumber)
                        .map(this::submittedSessionIds)
                        .orElse(Set.of());

        List<ModuleSlotDto> slots = sessionsForModules(List.of(module)).stream()
                .map(s -> toSlot(s, submittedSessionIds))
                .collect(Collectors.toList());

        return ModuleDetailResponseDto.builder()
                .id(module.getId())
                .moduleName(module.getModuleName())
                .moduleCode(module.getModuleCode())
                .lecturerName(lecturerName(module))
                .fileName(fileName(module))
                .filePath(module.getFilePath())
                .moduleType(moduleType(module))
                .slots(slots)
                .files(fileDtos(module))
                .build();
    }

    /**
     * Module detail for a learner, scoped to what they are actually enrolled on.
     *
     * Guides fan out by enrolment, so a module outside the learner's learnership is reported
     * as not found rather than forbidden — a 403 would confirm the module exists.
     */
    @Transactional(readOnly = true)
    public ModuleDetailResponseDto getModuleDetailsForLearner(Long moduleId, String studentNumber) {
        Learner learner = learnerRepository.findByLearnerCode(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Student number not found: " + studentNumber));

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with id: " + moduleId));

        if (!isEnrolledOn(learner, module)) {
            throw new ResourceNotFoundException("Module not found with id: " + moduleId);
        }

        return getModuleDetails(moduleId, studentNumber);
    }

    /**
     * A module file this learner is allowed to download, or 404.
     *
     * Enrolment is judged by the same rule that decides whether the module appears in their
     * list at all, so a file is downloadable exactly when the module it belongs to is visible.
     * Two rules would eventually disagree, and the disagreement would be a hole.
     *
     * Not found rather than forbidden, as everywhere else: ids are sequential, and a 403 on
     * someone else's confirms it exists.
     */
    @Transactional(readOnly = true)
    public ModuleFile requireModuleFileForLearner(Long fileId, String learnerCode) {
        Learner learner = learnerRepository.findByLearnerCode(learnerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Student number not found: " + learnerCode));
        ModuleFile file = moduleFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

        if (file.getModule() == null || !isEnrolledOn(learner, file.getModule())) {
            throw new ResourceNotFoundException("File not found with id: " + fileId);
        }
        return file;
    }

    /** A session whose task brief this learner may download, or 404. Same rule as above. */
    @Transactional(readOnly = true)
    public SubmissionSession requireSessionForLearner(Long sessionId, String learnerCode) {
        Learner learner = learnerRepository.findByLearnerCode(learnerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Student number not found: " + learnerCode));
        SubmissionSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        Module module = session.getAssignment() == null ? null : session.getAssignment().getModule();
        if (module == null || !isEnrolledOn(learner, module)) {
            throw new ResourceNotFoundException("Session not found with id: " + sessionId);
        }
        return session;
    }

    private boolean isEnrolledOn(Learner learner, Module module) {
        if (learner.getModules() != null && learner.getModules().stream()
                .anyMatch(m -> m.getId().equals(module.getId()))) {
            return true;
        }
        // Learners registered before per-module enrolment was populated are scoped by their
        // learnership instead, which is how getEnrolledModules() already lists their modules.
        return learner.getLearnership() != null
                && module.getCategory() != null
                && module.getCategory().getLearnership() != null
                && module.getCategory().getLearnership().getId().equals(learner.getLearnership().getId());
    }

    @Transactional(readOnly = true)
    public List<TimelineResponseDto> getLearnerTimeline(String studentNumber) {
        Learner learner = learnerRepository.findByLearnerCode(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Student number not found: " + studentNumber));

        if (learner.getLearnership() == null) {
            return Collections.emptyList();
        }

        List<Module> modules = moduleRepository.findByCategoryLearnershipId(learner.getLearnership().getId());
        if (modules.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> submittedSessionIds = submittedSessionIds(learner);

        return sessionsForModules(modules).stream()
                .filter(s -> s.getStatus() == SessionStatus.OPEN || s.getStatus() == SessionStatus.SCHEDULED)
                .sorted(Comparator.comparing(SubmissionSession::getEndTime))
                .map(s -> TimelineResponseDto.builder()
                        .sessionId(s.getId())
                        .moduleId(s.getAssignment().getModule().getId())
                        .moduleName(s.getAssignment().getModule().getModuleName())
                        .slotTitle(s.getAssignment().getTitle())
                        .description(s.getAssignment().getDescription())
                        .endTime(s.getEndTime())
                        .status(s.getStatus().name())
                        .submitted(submittedSessionIds.contains(s.getId()))
                        .taskFileName(s.getTaskFileName())
                        .hasBrief(hasBrief(s))
                        .build())
                .collect(Collectors.toList());
    }

    // --- helpers ---

    /** Sessions for these modules in one query, rather than every session in the database. */
    private List<SubmissionSession> sessionsForModules(Collection<Module> modules) {
        List<Long> moduleIds = modules.stream().map(Module::getId).toList();
        return sessionRepository.findByAssignmentModuleIdIn(moduleIds);
    }

    /** One query for the whole list, instead of loading every submission of every slot. */
    private Set<Long> submittedSessionIds(Learner learner) {
        return new HashSet<>(submissionRepository.findSessionIdsByLearnerId(learner.getId()));
    }

    private ModuleSlotDto toSlot(SubmissionSession s, Set<Long> submittedSessionIds) {
        return ModuleSlotDto.builder()
                .id(s.getId())
                .title(s.getAssignment().getTitle())
                .description(s.getAssignment().getDescription())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .sessionName(s.getSessionName())
                .status(s.getStatus().name())
                .isSubmitted(submittedSessionIds.contains(s.getId()))
                .taskFileName(s.getTaskFileName())
                .hasBrief(hasBrief(s))
                .build();
    }

    private static boolean hasBrief(SubmissionSession s) {
        return s.getTaskFilePath() != null && !s.getTaskFilePath().isBlank();
    }

    private static String lecturerName(Module m) {
        return m.getCategory() != null && m.getCategory().getLecturer() != null
                ? m.getCategory().getLecturer().getFullName() : "N/A";
    }

    private static String fileName(Module m) {
        return m.getFilePath() != null ? m.getFilePath().substring(m.getFilePath().lastIndexOf("/") + 1) : null;
    }

    private static String moduleType(Module m) {
        return m.getCategory() != null ? m.getCategory().getCategoryType() : "CORE";
    }

    private static List<ModuleFileDto> fileDtos(Module m) {
        if (m.getFiles() == null) {
            return Collections.emptyList();
        }
        return m.getFiles().stream()
                .map(f -> ModuleFileDto.builder()
                        .id(f.getId())
                        .title(f.getTitle())
                        .originalFilename(f.getOriginalFilename())
                        .fileType(f.getFileType())
                        .build())
                .collect(Collectors.toList());
    }
}
