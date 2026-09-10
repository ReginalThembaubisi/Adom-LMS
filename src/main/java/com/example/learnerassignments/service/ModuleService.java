package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.ModuleDetailResponseDto;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

        Long learnershipId = learner.getLearnership().getId();
        List<SubmissionSession> allSessions = sessionRepository.findAll();

        return moduleRepository.findAll().stream()
                .filter(m -> m.getCategory() != null && m.getCategory().getLearnership() != null 
                        && m.getCategory().getLearnership().getId().equals(learnershipId))
                .map(m -> {
                    List<ModuleSlotDto> slots = allSessions.stream()
                        .filter(s -> s.getAssignment() != null && s.getAssignment().getModule() != null
                                && s.getAssignment().getModule().getId().equals(m.getId())
                                && s.getStatus() == SessionStatus.OPEN)
                        .map(s -> {
                            boolean submitted = false;
                            if (studentNumber != null && !studentNumber.isBlank()) {
                                submitted = submissionRepository.findBySessionId(s.getId()).stream()
                                    .anyMatch(sub -> sub.getLearner() != null && studentNumber.equals(sub.getLearner().getLearnerCode()));
                            }
                            return ModuleSlotDto.builder()
                                .id(s.getId())
                                .title(s.getAssignment().getTitle())
                                .description(s.getAssignment().getDescription())
                                .endTime(s.getEndTime())
                                .sessionName(s.getSessionName())
                                .status(s.getStatus().name())
                                .isSubmitted(submitted)
                                .taskFileName(s.getTaskFileName())
                                .hasBrief(s.getTaskFilePath() != null && !s.getTaskFilePath().isBlank())
                                .build();
                        })
                        .collect(Collectors.toList());

                    return ModuleResponseDto.builder()
                        .id(m.getId())
                        .moduleName(m.getModuleName())
                        .moduleCode(m.getModuleCode())
                        .lecturerName(m.getCategory() != null && m.getCategory().getLecturer() != null ? m.getCategory().getLecturer().getFullName() : "N/A")
                        .fileName(m.getFilePath() != null ? m.getFilePath().substring(m.getFilePath().lastIndexOf("/") + 1) : null)
                        .filePath(m.getFilePath())
                        .moduleType(m.getCategory() != null ? m.getCategory().getCategoryType() : "CORE")
                        .files(m.getFiles() != null ? m.getFiles().stream()
                                .map(f -> com.example.learnerassignments.dto.ModuleFileDto.builder()
                                        .id(f.getId())
                                        .title(f.getTitle())
                                        .originalFilename(f.getOriginalFilename())
                                        .fileType(f.getFileType())
                                        .build())
                                .collect(Collectors.toList()) : java.util.Collections.emptyList())
                        .slots(slots)
                        .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ModuleDetailResponseDto getModuleDetails(Long moduleId, String studentNumber) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with id: " + moduleId));

        // Get all submission sessions (slots) whose assignments are linked to this module
        List<SubmissionSession> sessions = sessionRepository.findAll().stream()
                .filter(s -> s.getAssignment() != null && s.getAssignment().getModule() != null
                        && s.getAssignment().getModule().getId().equals(moduleId))
                .collect(Collectors.toList());

        List<ModuleSlotDto> slots = sessions.stream().map(s -> {
            boolean submitted = false;
            if (studentNumber != null && !studentNumber.isBlank()) {
                submitted = submissionRepository.findBySessionId(s.getId()).stream()
                        .anyMatch(sub -> sub.getLearner() != null && studentNumber.equals(sub.getLearner().getLearnerCode()));
            }

            return ModuleSlotDto.builder()
                    .id(s.getId())
                    .title(s.getAssignment().getTitle())
                    .description(s.getAssignment().getDescription())
                    .startTime(s.getStartTime())
                    .endTime(s.getEndTime())
                    .sessionName(s.getSessionName())
                    .status(s.getStatus().name())
                    .isSubmitted(submitted)
                    .taskFileName(s.getTaskFileName())
                    .hasBrief(s.getTaskFilePath() != null && !s.getTaskFilePath().isBlank())
                    .build();
        }).collect(Collectors.toList());

        return ModuleDetailResponseDto.builder()
                .id(module.getId())
                .moduleName(module.getModuleName())
                .moduleCode(module.getModuleCode())
                .lecturerName(module.getCategory() != null && module.getCategory().getLecturer() != null ? module.getCategory().getLecturer().getFullName() : "N/A")
                .fileName(module.getFilePath() != null ? module.getFilePath().substring(module.getFilePath().lastIndexOf("/") + 1) : null)
                .filePath(module.getFilePath())
                .moduleType(module.getCategory() != null ? module.getCategory().getCategoryType() : "CORE")
                .slots(slots)
                .files(module.getFiles() != null ? module.getFiles().stream()
                        .map(f -> com.example.learnerassignments.dto.ModuleFileDto.builder()
                                .id(f.getId())
                                .title(f.getTitle())
                                .originalFilename(f.getOriginalFilename())
                                .fileType(f.getFileType())
                                .build())
                        .collect(Collectors.toList()) : java.util.Collections.emptyList())
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

        Long learnershipId = learner.getLearnership().getId();
        java.util.Set<Long> enrolledModuleIds = moduleRepository.findAll().stream()
                .filter(m -> m.getCategory() != null && m.getCategory().getLearnership() != null 
                        && m.getCategory().getLearnership().getId().equals(learnershipId))
                .map(Module::getId)
                .collect(Collectors.toSet());

        return sessionRepository.findAll().stream()
                .filter(s -> s.getAssignment() != null && s.getAssignment().getModule() != null
                        && enrolledModuleIds.contains(s.getAssignment().getModule().getId()))
                .filter(s -> s.getStatus() == SessionStatus.OPEN || s.getStatus() == SessionStatus.SCHEDULED)
                .sorted(java.util.Comparator.comparing(SubmissionSession::getEndTime))
                .map(s -> {
                    boolean submitted = false;
                    if (studentNumber != null && !studentNumber.isBlank()) {
                        submitted = submissionRepository.findBySessionId(s.getId()).stream()
                                .anyMatch(sub -> sub.getLearner() != null && studentNumber.equals(sub.getLearner().getLearnerCode()));
                    }
                    return TimelineResponseDto.builder()
                            .sessionId(s.getId())
                            .moduleId(s.getAssignment().getModule().getId())
                            .moduleName(s.getAssignment().getModule().getModuleName())
                            .slotTitle(s.getAssignment().getTitle())
                            .description(s.getAssignment().getDescription())
                            .endTime(s.getEndTime())
                            .status(s.getStatus().name())
                            .submitted(submitted)
                            .taskFileName(s.getTaskFileName())
                            .hasBrief(s.getTaskFilePath() != null && !s.getTaskFilePath().isBlank())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
