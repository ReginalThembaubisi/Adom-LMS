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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    private final EnrolmentService enrolmentService;
    private final SubmissionGradingHistoryRepository gradingHistoryRepository;
    private final SignatureService signatureService;

    // Deliberately NOT the "uploads" directory: that one is mapped as a public static
    // resource handler for facilitator guides and assignment briefs. Learner submissions
    // written there were readable by anyone who guessed the filename, which is built from
    // the learner code and session id. They live outside the served tree and are reachable
    // only through the ownership-checked /api/submissions/{id}/view endpoint.
    @Value("${file.submission-dir:private-uploads}")
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
                // Stores the public_id, not the secure_url. The file goes up as an
                // authenticated resource, so the URL alone no longer opens it — every read
                // goes through the ownership check in SubmissionController and a signature
                // generated here.
                filePathString = cloudinaryService.uploadLearnerFile(file);
            } catch (IOException e) {
                // IllegalStateException, not a bare RuntimeException: this codebase's own
                // convention for "the server could not complete a valid request", which
                // GlobalExceptionHandler answers 500 with this exact message rather than the
                // generic one — found and fixed during the Phase 9 exception-handling audit,
                // the same audit that also fixed LecturerController's raw RuntimeExceptions.
                throw new IllegalStateException("Could not upload file to Cloudinary. Please try again!", e);
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
                throw new IllegalStateException("Could not store file " + storedFilename + ". Please try again!", e);
            }
        }

        // 5b. Hash the bytes we actually received, before anything else can touch them.
        //     Phase 9 signs against this without re-fetching the file; a hash taken later,
        //     from a file downloaded later, would prove only what that file is by then.
        String sha256 = ContentHash.of(file);

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
                .sha256(sha256)
                .build();

        Submission savedSubmission = submissionRepository.save(submission);

        // A resubmission to the same session supersedes any earlier attempt at it. A signature
        // already placed on that earlier submission attested to work that is no longer this
        // learner's current answer to this session, so it is withdrawn rather than left
        // standing against superseded work — the same reasoning LearnerDocumentService applies
        // to a new document version. Each Submission row is immutable once created (a
        // resubmission is always a new row, never an edit to this one), so "earlier" here means
        // every other submission this learner has on this session, not a version of this one.
        submissionRepository.findByLearner_IdAndSession_IdOrderBySubmittedAtDesc(learner.getId(), session.getId())
                .stream()
                .filter(s -> !s.getId().equals(savedSubmission.getId()))
                .forEach(previous -> signatureService.revokeActiveSignature(
                        SignableType.SUBMISSION, previous.getId(), "Superseded by a resubmission."));

        // Accepting the work is the last honest moment to record that they were on the module.
        // The roster is fixed at module creation now, but if that is ever missed again a
        // submission must not be the thing that goes unrecorded.
        enrolmentService.ensureEnrolled(learner, assignment.getModule());

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
                .submittedAt(now)
                .status(status)
                .message(successMessage)
                .build();
    }

    /** The whole session, for callers with no scope restriction — admins and lecturers. */
    @Transactional(readOnly = true)
    public SessionSubmissionOverviewResponse getSessionSubmissionsOverview(Long sessionId) {
        return getSessionSubmissionsOverview(sessionId, null);
    }

    /**
     * A session's submissions, restricted to a set of learners.
     *
     * Being allowed to open a session is not the same as being allowed to see everyone in it:
     * an assessor assigned two learners on a module may reach a session on that module while
     * the rest of the cohort in it remains none of their business. A null set means no
     * restriction; an empty set means no learners, which is what an unassigned account gets.
     */
    @Transactional(readOnly = true)
    public SessionSubmissionOverviewResponse getSessionSubmissionsOverview(
            Long sessionId, java.util.Set<Long> visibleLearnerIds) {
        SubmissionSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission session not found with id: " + sessionId));

        Assignment assignment = session.getAssignment();
        Long moduleId = assignment.getModule() != null ? assignment.getModule().getId() : null;

        // Who submitted is read from the submissions, not from the module roster.
        //
        // This used to walk the roster and attach each learner's submission, which assumed
        // that everyone who can submit is in the learner_modules join table. Nothing enforces
        // that: registration never writes those rows, and submitAssignment never checks them.
        // A learner scoped to the module by their learnership — which is how the portal decides
        // what they can see — submitted successfully, was shown "Submitted", and never appeared
        // in the facilitator's console. The work sat in the database with nobody able to mark
        // it, and neither side had any way to notice.
        //
        // The roster still answers the question it can answer: who has not submitted yet.
        List<Submission> submissions = submissionRepository.findBySessionId(sessionId);

        Map<Long, List<Submission>> submissionsByLearnerMap = submissions.stream()
                .filter(s -> s.getLearner() != null)
                .collect(Collectors.groupingBy(s -> s.getLearner().getId()));

        List<SubmittedLearnerDto> submittedList = new ArrayList<>();
        List<UnsubmittedLearnerDto> unsubmittedList = new ArrayList<>();

        for (Map.Entry<Long, List<Submission>> entry : submissionsByLearnerMap.entrySet()) {
            // Scoping still narrows this: an assessor holding no assignment rows sees nothing,
            // exactly as before. Making submissions impossible to lose must not make them
            // visible to people who should not see them.
            if (visibleLearnerIds != null && !visibleLearnerIds.contains(entry.getKey())) {
                continue;
            }
            Submission latestSubmission = entry.getValue().stream()
                    .max(Comparator.comparing(Submission::getSubmittedAt))
                    .orElse(entry.getValue().get(0));
            Learner learner = latestSubmission.getLearner();

            submittedList.add(SubmittedLearnerDto.builder()
                    .submissionId(latestSubmission.getId())
                    .learnerId(learner.getId())
                    .learnerCode(learner.getLearnerCode())
                    .fullName(learner.getFullName())
                    .cohort(learner.getCohort())
                    .submittedAt(latestSubmission.getSubmittedAt())
                    .status(latestSubmission.getStatus())
                    .originalFilename(latestSubmission.getOriginalFilename())
                    .feedback(latestSubmission.getFeedback())
                    .gradedAt(latestSubmission.getGradedAt())
                    .gradedByRole(latestSubmission.getGradedByRole())
                    .gradedByName(latestSubmission.getGradedByName())
                    .marksAwarded(latestSubmission.getMarksAwarded())
                    .hasMarkedCopy(latestSubmission.getMarkedFilePath() != null)
                    .hasAnnotations(latestSubmission.getAnnotationsJson() != null)
                    .feedbackReleased(latestSubmission.isMarkingVisibleToLearner())
                    .build());
        }

        // What a release would actually do, counted the same way the release itself counts:
        // marked, learner-facing, not yet published. A facilitator deciding whether to press
        // the button needs the number it will act on, not the number of rows on screen.
        int heldCount = 0;
        int releasedCount = 0;
        Set<Long> wouldNotify = new HashSet<>();
        for (Submission submission : submissions) {
            if (submission.getGradedAt() == null
                    || submission.getFeedbackVisibility() == FeedbackVisibility.INTERNAL) {
                continue;
            }
            if (visibleLearnerIds != null && submission.getLearner() != null
                    && !visibleLearnerIds.contains(submission.getLearner().getId())) {
                continue;
            }
            if (submission.getFeedbackStatus() == FeedbackStatus.PUBLISHED) {
                releasedCount++;
            } else {
                heldCount++;
                if (submission.getLearner() != null) {
                    wouldNotify.add(submission.getLearner().getId());
                }
            }
        }

        submittedList.sort(Comparator.comparing(
                SubmittedLearnerDto::getFullName, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Learner> rosterLearners = moduleId != null
                ? learnerRepository.findByModules_Id(moduleId)
                : learnerRepository.findAll();

        for (Learner learner : rosterLearners) {
            if (submissionsByLearnerMap.containsKey(learner.getId())) {
                continue;
            }
            if (visibleLearnerIds != null && !visibleLearnerIds.contains(learner.getId())) {
                continue;
            }
            unsubmittedList.add(UnsubmittedLearnerDto.builder()
                    .learnerId(learner.getId())
                    .learnerCode(learner.getLearnerCode())
                    .fullName(learner.getFullName())
                    .cohort(learner.getCohort())
                    .build());
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
                .totalLearners(submittedList.size() + unsubmittedList.size())
                .heldCount(heldCount)
                .releasedCount(releasedCount)
                .wouldNotifyCount(wouldNotify.size())
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

        Long moduleId = assignment.getModule() != null ? assignment.getModule().getId() : null;
        List<Learner> allLearners = moduleId != null
                ? learnerRepository.findByModules_Id(moduleId)
                : learnerRepository.findAll();

        List<SubmissionSession> sessions = sessionRepository.findByAssignmentId(assignmentId);

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
                        .originalFilename(latestSubmission.getOriginalFilename())
                        .feedback(latestSubmission.getFeedback())
                        .gradedAt(latestSubmission.getGradedAt())
                        .gradedByRole(latestSubmission.getGradedByRole())
                        .gradedByName(latestSubmission.getGradedByName())
                        .marksAwarded(latestSubmission.getMarksAwarded())
                        .hasMarkedCopy(latestSubmission.getMarkedFilePath() != null)
                        .hasAnnotations(latestSubmission.getAnnotationsJson() != null)
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
                .totalLearners(submittedList.size() + unsubmittedList.size())
                .submittedCount(submittedList.size())
                .unsubmittedCount(unsubmittedList.size())
                .submitted(submittedList)
                .unsubmitted(unsubmittedList)
                .build();
    }

    @Transactional
    public SubmissionResponse gradeSubmission(Long submissionId, GradeSubmissionRequest request, String graderRole, String graderName) {
        if (request.getOutcome() != SubmissionStatus.COMPETENT && request.getOutcome() != SubmissionStatus.NOT_YET_COMPETENT) {
            throw new InvalidFileException("Outcome must be COMPETENT or NOT_YET_COMPETENT.");
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        LocalDateTime now = LocalDateTime.now();

        // A moderator's report is for the record and the export, not the portal. It still
        // changes the outcome — that is what moderation is for — but its written report does
        // not replace what the facilitator told the learner. One submission has one feedback
        // field and several reports over its life; treating the moderator's as the learner's
        // would delete the facilitator's feedback from the learner's view.
        boolean internalReport = "MODERATOR".equalsIgnoreCase(graderRole);

        submission.setStatus(request.getOutcome());
        submission.setMarksAwarded(request.getMarksAwarded());
        submission.setGradedAt(now);
        submission.setGradedByRole(graderRole);
        submission.setGradedByName(graderName);

        if (!internalReport) {
            submission.setFeedback(request.getFeedback());
            submission.setFeedbackVisibility(FeedbackVisibility.LEARNER);
        } else if (submission.getFeedback() == null || submission.getFeedback().isBlank()) {
            // Moderated with nothing the learner was ever meant to read.
            submission.setFeedbackVisibility(FeedbackVisibility.INTERNAL);
        }

        // Marking is a draft until somebody releases the session. The exception is work whose
        // feedback the learner has already been shown: a correction to released marking reaches
        // them immediately rather than disappearing back into a draft they cannot see, because
        // retracting feedback somebody has read is worse than publishing a change they did not
        // ask for.
        if (submission.getFeedbackStatus() != FeedbackStatus.PUBLISHED) {
            submission.setFeedbackStatus(FeedbackStatus.DRAFT);
        }

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
                .feedbackVisibility(internalReport ? FeedbackVisibility.INTERNAL : FeedbackVisibility.LEARNER)
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
        String markedSha256 = ContentHash.of(file);
        // A marked copy carries the assessor's decision on somebody's work, so it is stored
        // exactly like the original: authenticated, addressed by public_id.
        String publicId = cloudinaryService.uploadLearnerFile(file);
        submission.setMarkedFilePath(publicId);
        submission.setMarkedSha256(markedSha256);
        submissionRepository.save(submission);
        return publicId;
    }

    @Transactional(readOnly = true)
    public Submission getSubmission(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));
    }

    /**
     * A submission, but only if it belongs to this learner.
     *
     * Someone else's submission is reported as not found, never as forbidden: a 403 would
     * confirm the id exists, which is enough to enumerate the cohort's record ids.
     */
    @Transactional(readOnly = true)
    public Submission requireOwnedByLearner(Long submissionId, Long learnerId) {
        Submission submission = getSubmission(submissionId);
        if (submission.getLearner() == null || !submission.getLearner().getId().equals(learnerId)) {
            throw new ResourceNotFoundException("Submission not found with id: " + submissionId);
        }
        return submission;
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

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    @Transactional
    public void saveAnnotations(Long id, String json) {
        Submission s = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));
        s.setAnnotationsJson(json);
        submissionRepository.save(s);
    }

    @Transactional(readOnly = true)
    /**
     * The saved stroke data for a submission, or null when it has not been marked yet.
     *
     * Deliberately not Optional.map: mapping to a null annotationsJson collapses to an empty
     * Optional, which made an unmarked submission indistinguishable from a missing one and
     * answered "Submission not found" for a record that plainly exists. That also blunted the
     * ownership signal on this endpoint, where a 404 is supposed to mean "not yours".
     */
    public String getAnnotationsJson(Long id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));
        return submission.getAnnotationsJson();
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
