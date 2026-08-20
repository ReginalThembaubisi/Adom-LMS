package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.exception.InvalidFileException;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.AssignmentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.SubmissionGradingHistoryRepository;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private final SubmissionGradingHistoryRepository gradingHistoryRepository;

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
                        .gradedByRole(latestSubmission.getGradedByRole())
                        .gradedByName(latestSubmission.getGradedByName())
                        .marksAwarded(latestSubmission.getMarksAwarded())
                        .markedFilePath(latestSubmission.getMarkedFilePath())
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
                        .gradedByRole(latestSubmission.getGradedByRole())
                        .gradedByName(latestSubmission.getGradedByName())
                        .marksAwarded(latestSubmission.getMarksAwarded())
                        .markedFilePath(latestSubmission.getMarkedFilePath())
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
    public SubmissionResponse gradeSubmission(Long submissionId, GradeSubmissionRequest request, String graderRole, String graderName) {
        if (request.getOutcome() != SubmissionStatus.COMPETENT && request.getOutcome() != SubmissionStatus.NOT_YET_COMPETENT) {
            throw new InvalidFileException("Outcome must be COMPETENT or NOT_YET_COMPETENT.");
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        LocalDateTime now = LocalDateTime.now();

        submission.setStatus(request.getOutcome());
        submission.setFeedback(request.getFeedback());
        submission.setMarksAwarded(request.getMarksAwarded());
        submission.setGradedAt(now);
        submission.setGradedByRole(graderRole);
        submission.setGradedByName(graderName);

        Submission saved = submissionRepository.save(submission);

        // Every grading action is appended here rather than only overwriting the fields above,
        // so a later grader (e.g. a moderator reviewing a facilitator's mark) can see what came
        // before instead of it being silently replaced with no trace.
        gradingHistoryRepository.save(SubmissionGradingHistory.builder()
                .submission(saved)
                .outcome(request.getOutcome())
                .feedback(request.getFeedback())
                .marksAwarded(request.getMarksAwarded())
                .gradedByRole(graderRole)
                .gradedByName(graderName)
                .gradedAt(now)
                .build());

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

    // Uploads the flattened, annotated copy of a submission's document (drawn client-side)
    // alongside the original — the original filePath is never overwritten, so the learner's
    // untouched submission is always still there.
    @Transactional
    public String uploadMarkedCopy(Long submissionId, MultipartFile file) throws IOException {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        if (!cloudinaryService.isConfigured()) {
            throw new IllegalStateException("File storage is not configured.");
        }
        String url = cloudinaryService.uploadFile(file);
        submission.setMarkedFilePath(url);
        submissionRepository.save(submission);
        return url;
    }

    @Transactional(readOnly = true)
    public Submission getSubmission(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<GradingHistoryEntryDto> getGradingHistory(Long submissionId) {
        return gradingHistoryRepository.findBySubmission_IdOrderByGradedAtAsc(submissionId).stream()
                .map(h -> GradingHistoryEntryDto.builder()
                        .outcome(h.getOutcome())
                        .feedback(h.getFeedback())
                        .marksAwarded(h.getMarksAwarded())
                        .gradedByRole(h.getGradedByRole())
                        .gradedByName(h.getGradedByName())
                        .gradedAt(h.getGradedAt())
                        .build())
                .collect(Collectors.toList());
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

    // Cloudinary serves "raw" resources (how submission files are stored) without a
    // reliable, inline-renderable Content-Type — embedding the raw Cloudinary URL directly
    // in an <iframe> left the viewer blank instead of showing the document. Fetching the
    // bytes ourselves and re-serving them with a Content-Type we control fixes that.
    public byte[] fetchExternalFile(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                // The body has been empty every time so far — Cloudinary sometimes puts the
                // real reason for a CDN-edge rejection in a response header instead (e.g.
                // x-cld-error). Surface both, plus which URL was actually requested, so this
                // is finally diagnosable instead of another guess.
                String cloudinaryBody = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                if (cloudinaryBody.length() > 300) {
                    cloudinaryBody = cloudinaryBody.substring(0, 300);
                }
                String headers = response.headers().map().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("; "));
                throw new ResourceNotFoundException(
                        "Could not retrieve file from storage (status " + response.statusCode() + ") url=" + url
                                + " body=" + cloudinaryBody + " headers=" + headers);
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new ResourceNotFoundException("Could not retrieve file from storage: " + e.getMessage());
        }
    }

    public String resolveContentType(String originalFilename) {
        String extension = getFileExtension(originalFilename).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
