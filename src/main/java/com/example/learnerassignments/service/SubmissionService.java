package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.exception.InvalidFileException;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.AssignmentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.repository.SubmissionSessionRepository;
import com.example.learnerassignments.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final LearnerRepository learnerRepository;
    private final AssignmentRepository assignmentRepository;
    private final CloudinaryService cloudinaryService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "doc", "docx");

    @Transactional
    public SubmissionResponse submitAssignment(String learnerCode, Long sessionId, MultipartFile file) {
        // 1. Validate Learner code
        Learner learner = learnerRepository.findByLearnerCode(learnerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Learner code not found, please check with your facilitator."));

        // 2. Validate Session ID
        SubmissionSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission session not found with id: " + sessionId));

        // 3. Validate Session Open Status & Timing
        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() == SessionStatus.CLOSED || now.isAfter(session.getEndTime())) {
            throw new InvalidFileException("This submission session is closed.");
        }
        if (session.getStatus() == SessionStatus.SCHEDULED || now.isBefore(session.getStartTime())) {
            throw new InvalidFileException("This session hasn't opened yet, it opens at " + session.getStartTime());
        }

        // 4. Validate File
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file cannot be empty.");
        }

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String extension = getFileExtension(originalFilename).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException("Invalid file format. Only PDF, DOC, and DOCX files are accepted.");
        }

        // 5. Save File (Cloudinary if configured, otherwise local disk fallback)
        String filePathString;
        if (cloudinaryService.isConfigured()) {
            try {
                filePathString = cloudinaryService.uploadFile(file);
            } catch (IOException e) {
                throw new RuntimeException("Could not upload file to Cloudinary. Please try again!", e);
            }
        } else {
            String storedFilename = String.format("%s_%d_%s", learnerCode, sessionId, originalFilename);
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            try {
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                Path targetLocation = uploadPath.resolve(storedFilename);
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
                }
                filePathString = targetLocation.toString();
            } catch (IOException e) {
                throw new RuntimeException("Could not store file " + storedFilename + ". Please try again!", e);
            }
        }

        // 6. Calculate Submission Status (LATE if after assignment due date)
        Assignment assignment = session.getAssignment();
        SubmissionStatus status = now.isAfter(assignment.getDueDate())
                ? SubmissionStatus.LATE
                : SubmissionStatus.SUBMITTED;

        // 7. Save Submission Entity
        Submission submission = Submission.builder()
                .learner(learner)
                .session(session)
                .filePath(filePathString)
                .originalFilename(originalFilename)
                .submittedAt(now)
                .status(status)
                .build();

        Submission savedSubmission = submissionRepository.save(submission);

        String successMessage = String.format(
                "Submission received successfully for %s on session '%s' (%s) at %s (Status: %s).",
                learner.getFullName(),
                session.getSessionName(),
                assignment.getTitle(),
                now,
                status
        );

        return SubmissionResponse.builder()
                .id(savedSubmission.getId())
                .learnerCode(learner.getLearnerCode())
                .learnerName(learner.getFullName())
                .sessionId(session.getId())
                .sessionName(session.getSessionName())
                .assignmentId(assignment.getId())
                .assignmentTitle(assignment.getTitle())
                .originalFilename(originalFilename)
                .filePath(savedSubmission.getFilePath())
                .submittedAt(now)
                .status(status)
                .message(successMessage)
                .build();
    }

    @Transactional(readOnly = true)
    public SessionSubmissionOverviewResponse getSessionSubmissionsOverview(Long sessionId) {
        SubmissionSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission session not found with id: " + sessionId));

        Assignment assignment = session.getAssignment();
        List<Learner> allLearners = learnerRepository.findAll();
        List<Submission> submissions = submissionRepository.findBySessionId(sessionId);

        Map<Long, List<Submission>> submissionsByLearnerMap = submissions.stream()
                .collect(Collectors.groupingBy(s -> s.getLearner().getId()));

        List<SubmittedLearnerDto> submittedList = new ArrayList<>();
        List<UnsubmittedLearnerDto> unsubmittedList = new ArrayList<>();

        for (Learner learner : allLearners) {
            List<Submission> learnerSubmissions = submissionsByLearnerMap.get(learner.getId());
            if (learnerSubmissions != null && !learnerSubmissions.isEmpty()) {
                Submission latestSubmission = learnerSubmissions.stream()
                        .max(Comparator.comparing(Submission::getSubmittedAt))
                        .orElse(learnerSubmissions.get(0));

                submittedList.add(SubmittedLearnerDto.builder()
                        .submissionId(latestSubmission.getId())
                        .learnerId(learner.getId())
                        .learnerCode(learner.getLearnerCode())
                        .fullName(learner.getFullName())
                        .cohort(learner.getCohort())
                        .submittedAt(latestSubmission.getSubmittedAt())
                        .status(latestSubmission.getStatus())
                        .filePath(latestSubmission.getFilePath())
                        .originalFilename(latestSubmission.getOriginalFilename())
                        .feedback(latestSubmission.getFeedback())
                        .gradedAt(latestSubmission.getGradedAt())
                        .build());
            } else {
                unsubmittedList.add(UnsubmittedLearnerDto.builder()
                        .learnerId(learner.getId())
                        .learnerCode(learner.getLearnerCode())
                        .fullName(learner.getFullName())
                        .cohort(learner.getCohort())
                        .build());
            }
        }

        return SessionSubmissionOverviewResponse.builder()
                .sessionId(session.getId())
                .sessionName(session.getSessionName())
                .sessionStatus(session.getStatus())
                .assignmentId(assignment.getId())
                .assignmentTitle(assignment.getTitle())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .dueDate(assignment.getDueDate())
                .totalLearners(allLearners.size())
                .submittedCount(submittedList.size())
                .unsubmittedCount(unsubmittedList.size())
                .submitted(submittedList)
                .unsubmitted(unsubmittedList)
                .build();
    }

    @Transactional(readOnly = true)
    public AssignmentSubmissionOverviewResponse getAssignmentSubmissionsOverview(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + assignmentId));

        List<Learner> allLearners = learnerRepository.findAll();

        // Get all submissions for sessions linked to this assignment
        List<SubmissionSession> sessions = sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(s -> s.getAssignment().getId().equals(assignmentId))
                .collect(Collectors.toList());

        List<Long> sessionIds = sessions.stream().map(SubmissionSession::getId).collect(Collectors.toList());

        List<Submission> submissions = sessionIds.isEmpty() ? Collections.emptyList() :
                sessionIds.stream()
                        .flatMap(sId -> submissionRepository.findBySessionId(sId).stream())
                        .collect(Collectors.toList());

        Map<Long, List<Submission>> submissionsByLearnerMap = submissions.stream()
                .collect(Collectors.groupingBy(s -> s.getLearner().getId()));

        List<SubmittedLearnerDto> submittedList = new ArrayList<>();
        List<UnsubmittedLearnerDto> unsubmittedList = new ArrayList<>();

        for (Learner learner : allLearners) {
            List<Submission> learnerSubmissions = submissionsByLearnerMap.get(learner.getId());
            if (learnerSubmissions != null && !learnerSubmissions.isEmpty()) {
                Submission latestSubmission = learnerSubmissions.stream()
                        .max(Comparator.comparing(Submission::getSubmittedAt))
                        .orElse(learnerSubmissions.get(0));

                submittedList.add(SubmittedLearnerDto.builder()
                        .submissionId(latestSubmission.getId())
                        .learnerId(learner.getId())
                        .learnerCode(learner.getLearnerCode())
                        .fullName(learner.getFullName())
                        .cohort(learner.getCohort())
                        .submittedAt(latestSubmission.getSubmittedAt())
                        .status(latestSubmission.getStatus())
                        .filePath(latestSubmission.getFilePath())
                        .originalFilename(latestSubmission.getOriginalFilename())
                        .feedback(latestSubmission.getFeedback())
                        .gradedAt(latestSubmission.getGradedAt())
                        .build());
            } else {
                unsubmittedList.add(UnsubmittedLearnerDto.builder()
                        .learnerId(learner.getId())
                        .learnerCode(learner.getLearnerCode())
                        .fullName(learner.getFullName())
                        .cohort(learner.getCohort())
                        .build());
            }
        }

        return AssignmentSubmissionOverviewResponse.builder()
                .assignmentId(assignment.getId())
                .assignmentTitle(assignment.getTitle())
                .dueDate(assignment.getDueDate())
                .totalLearners(allLearners.size())
                .submittedCount(submittedList.size())
                .unsubmittedCount(unsubmittedList.size())
                .submitted(submittedList)
                .unsubmitted(unsubmittedList)
                .build();
    }

    @Transactional(readOnly = true)
    public Map.Entry<Submission, Resource> loadSubmissionResource(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        try {
            Path filePath = Paths.get(submission.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return new AbstractMap.SimpleEntry<>(submission, resource);
            } else {
                throw new ResourceNotFoundException("Could not read file for submission id: " + submissionId);
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("File path invalid for submission id: " + submissionId);
        }
    }

    @Transactional
    public SubmissionResponse gradeSubmission(Long submissionId, GradeSubmissionRequest request) {
        if (request.getOutcome() != SubmissionStatus.COMPETENT && request.getOutcome() != SubmissionStatus.NOT_YET_COMPETENT) {
            throw new InvalidFileException("Outcome must be COMPETENT or NOT_YET_COMPETENT.");
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        submission.setStatus(request.getOutcome());
        submission.setFeedback(request.getFeedback());
        submission.setGradedAt(LocalDateTime.now());

        Submission saved = submissionRepository.save(submission);

        return SubmissionResponse.builder()
                .id(saved.getId())
                .learnerCode(saved.getLearner().getLearnerCode())
                .learnerName(saved.getLearner().getFullName())
                .sessionId(saved.getSession().getId())
                .sessionName(saved.getSession().getSessionName())
                .originalFilename(saved.getOriginalFilename())
                .status(saved.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public Submission getSubmission(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));
    }

    public Resource loadLocalResource(String pathStr) {
        try {
            Path path = Paths.get(pathStr).normalize();
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Could not read file at path: " + pathStr);
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("Invalid file path: " + pathStr);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
