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
                                .taskFilePath(s.getTaskFilePath())
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
                                        .filePath(f.getFilePath())
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
                    .taskFilePath(s.getTaskFilePath())
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
                                .filePath(f.getFilePath())
                                .originalFilename(f.getOriginalFilename())
                                .fileType(f.getFileType())
                                .build())
                        .collect(Collectors.toList()) : java.util.Collections.emptyList())
                .build();
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
                            .build();
                })
                .collect(Collectors.toList());
    }
}
