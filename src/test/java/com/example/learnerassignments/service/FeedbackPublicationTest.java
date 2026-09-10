package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.GradeSubmissionRequest;
import com.example.learnerassignments.dto.LearnerFeedbackDto;
import com.example.learnerassignments.dto.StudentSubmissionHistoryDto;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5's acceptance criteria, plus the one it does not state but which matters more than any
 * of them: shipping this must not take back feedback a learner has already read.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackPublicationTest {

    @Autowired SubmissionService submissionService;
    @Autowired LearnerService learnerService;
    @Autowired FeedbackReleaseService releaseService;
    @Autowired FeedbackPublicationBackfill publicationBackfill;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired NotificationRepository notificationRepository;

    @Test
    @DisplayName("Draft marking is invisible to the learner — outcome, marks and comment together")
    void draftMarkingIsInvisible() {
        World w = seed();
        grade(w.submissionId, "FACILITATOR", "Good work", 80);

        StudentSubmissionHistoryDto row = history(w).get(0);

        // Not just the comment. An outcome with no explanation would be worse than silence.
        assertThat(row.getFeedback()).isNull();
        assertThat(row.getMarksAwarded()).isNull();
        assertThat(row.getGradedAt()).isNull();
        assertThat(row.getGradedByName()).isNull();
        assertThat(row.getStatus()).isEqualTo("SUBMITTED");
        assertThat(learnerService.getReleasedFeedback(w.learnerCode)).isEmpty();
    }

    @Test
    @DisplayName("Releasing the session makes it visible")
    void releaseMakesItVisible() {
        World w = seed();
        grade(w.submissionId, "FACILITATOR", "Good work", 80);

        releaseService.release(w.sessionId);

        StudentSubmissionHistoryDto row = history(w).get(0);
        assertThat(row.getFeedback()).isEqualTo("Good work");
        assertThat(row.getMarksAwarded()).isEqualTo(80);
        assertThat(row.getStatus()).isEqualTo("COMPETENT");

        List<LearnerFeedbackDto> feedback = learnerService.getReleasedFeedback(w.learnerCode);
        assertThat(feedback).hasSize(1);
        assertThat(feedback.get(0).getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("A moderator's report never reaches the learner, and does not erase the facilitator's")
    void moderatorReportIsInternal() {
        World w = seed();
        grade(w.submissionId, "FACILITATOR", "Facilitator says well done", 80);
        releaseService.release(w.sessionId);
        grade(w.submissionId, "MODERATOR", "Sampling note for the SETA file", 75);

        List<LearnerFeedbackDto> feedback = learnerService.getReleasedFeedback(w.learnerCode);

        assertThat(feedback).hasSize(1);
        // The moderator's words are not the learner's to read...
        assertThat(feedback.get(0).getFeedback()).isEqualTo("Facilitator says well done");
        assertThat(feedback.get(0).getFeedback()).doesNotContain("SETA");
        // ...but their report is on the record for the export.
        assertThat(submissionService.getGradingHistory(w.submissionId))
                .anySatisfy(h -> assertThat(h.getFeedback()).isEqualTo("Sampling note for the SETA file"));
    }

    @Test
    @DisplayName("Releasing notifies each learner once, and names nobody else")
    void releaseNotifiesEachLearnerOnce() {
        World w = seed();
        Learner other = extraLearner(w, "OTHER");
        Submission second = submissionRepository.save(Submission.builder()
                .learner(other).session(sessionRepository.findById(w.sessionId).orElseThrow())
                .filePath("lms_secure/b").originalFilename("b.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());
        // The same learner twice in one session must still be told once.
        Submission third = submissionRepository.save(Submission.builder()
                .learner(other).session(sessionRepository.findById(w.sessionId).orElseThrow())
                .filePath("lms_secure/c").originalFilename("c.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());
        grade(w.submissionId, "FACILITATOR", "a", 70);
        grade(second.getId(), "FACILITATOR", "b", 60);
        grade(third.getId(), "FACILITATOR", "c", 65);

        FeedbackReleaseService.ReleaseResult result = releaseService.release(w.sessionId);

        assertThat(result.notified()).isEqualTo(2);
        List<Notification> forOther = notificationRepository
                .findByUserIdAndUserRoleOrderByCreatedAtDesc(other.getId(), "LEARNER");
        assertThat(forOther).hasSize(1);
        // Nobody else's name, and no link — learners were told this system never sends one.
        assertThat(forOther.get(0).getBody()).doesNotContain(w.learnerName);
        assertThat(forOther.get(0).getBody()).doesNotContain("http");
    }

    @Test
    @DisplayName("Releasing twice does not notify anyone a second time")
    void releasingTwiceIsSafe() {
        World w = seed();
        grade(w.submissionId, "FACILITATOR", "Good work", 80);

        releaseService.release(w.sessionId);
        FeedbackReleaseService.ReleaseResult second = releaseService.release(w.sessionId);

        // A facilitator who marks three more scripts and presses release again should not
        // re-notify the whole cohort.
        assertThat(second.published()).isZero();
        assertThat(second.notified()).isZero();
        assertThat(second.alreadyPublished()).isEqualTo(1);
    }

    @Test
    @DisplayName("Releasing a half-marked session says so instead of claiming everything went out")
    void unmarkedWorkIsReported() {
        World w = seed();
        Learner other = extraLearner(w, "UNMARKED");
        submissionRepository.save(Submission.builder()
                .learner(other).session(sessionRepository.findById(w.sessionId).orElseThrow())
                .filePath("lms_secure/d").originalFilename("d.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());
        grade(w.submissionId, "FACILITATOR", "Good work", 80);

        FeedbackReleaseService.ReleaseResult result = releaseService.release(w.sessionId);

        assertThat(result.published()).isEqualTo(1);
        assertThat(result.unmarked()).isEqualTo(1);
    }

    @Test
    @DisplayName("Correcting released marking reaches the learner rather than vanishing")
    void remarkingPublishedWorkStaysPublished() {
        World w = seed();
        grade(w.submissionId, "FACILITATOR", "First take", 50);
        releaseService.release(w.sessionId);

        grade(w.submissionId, "FACILITATOR", "Corrected — I misread page 2", 70);

        // Retracting feedback somebody has read is worse than showing them a change.
        List<LearnerFeedbackDto> feedback = learnerService.getReleasedFeedback(w.learnerCode);
        assertThat(feedback).hasSize(1);
        assertThat(feedback.get(0).getFeedback()).isEqualTo("Corrected — I misread page 2");
        assertThat(feedback.get(0).getMarksAwarded()).isEqualTo(70);
    }

    @Test
    @DisplayName("Marking that was visible before this phase is not taken away by it")
    void backfillDoesNotRetractReadFeedback() {
        World w = seed();
        // Graded under the old behaviour: visible to the learner, and DRAFT because nothing
        // read that column yet. Phase 2's backfill missed this shape — no marked copy, and in
        // this case not even a comment, just an outcome and marks.
        Submission s = submissionRepository.findById(w.submissionId).orElseThrow();
        s.setStatus(SubmissionStatus.COMPETENT);
        s.setMarksAwarded(90);
        s.setGradedAt(LocalDateTime.now().minusDays(3));
        s.setGradedByRole("FACILITATOR");
        s.setGradedByName("Dr Ngobeni");
        s.setFeedbackStatus(FeedbackStatus.DRAFT);
        s.setFeedbackVisibility(FeedbackVisibility.LEARNER);
        submissionRepository.save(s);

        publicationBackfill.run();

        List<LearnerFeedbackDto> feedback = learnerService.getReleasedFeedback(w.learnerCode);
        assertThat(feedback).hasSize(1);
        assertThat(feedback.get(0).getMarksAwarded()).isEqualTo(90);
        // Dated when it became visible, not when the backfill ran.
        assertThat(feedback.get(0).getPublishedAt()).isEqualTo(s.getGradedAt());
    }

    @Test
    @DisplayName("The backfill never publishes an internal report, and is idempotent")
    void backfillLeavesInternalAloneAndRepeats() {
        World w = seed();
        Submission s = submissionRepository.findById(w.submissionId).orElseThrow();
        s.setGradedAt(LocalDateTime.now().minusDays(1));
        s.setFeedbackStatus(FeedbackStatus.DRAFT);
        s.setFeedbackVisibility(FeedbackVisibility.INTERNAL);
        submissionRepository.save(s);

        publicationBackfill.run();
        assertThat(submissionRepository.findById(w.submissionId).orElseThrow().getFeedbackStatus())
                .isEqualTo(FeedbackStatus.DRAFT);

        publicationBackfill.run();
        assertThat(learnerService.getReleasedFeedback(w.learnerCode)).isEmpty();
    }

    @Test
    @DisplayName("Unmarked work is untouched by the backfill")
    void backfillIgnoresUnmarkedWork() {
        World w = seed();

        publicationBackfill.run();

        assertThat(submissionRepository.findById(w.submissionId).orElseThrow().getPublishedAt()).isNull();
        assertThat(learnerService.getReleasedFeedback(w.learnerCode)).isEmpty();
    }

    // --- helpers ---

    private List<StudentSubmissionHistoryDto> history(World w) {
        return learnerService.getLearnerSubmissions(w.learnerCode);
    }

    private void grade(Long submissionId, String role, String feedback, int marks) {
        GradeSubmissionRequest request = new GradeSubmissionRequest();
        request.setOutcome(SubmissionStatus.COMPETENT);
        request.setFeedback(feedback);
        request.setMarksAwarded(marks);
        submissionService.gradeSubmission(submissionId, request, role, "Dr Ngobeni");
    }

    private record World(Long learnerId, String learnerCode, String learnerName,
                         Long sessionId, Long submissionId, Long learnershipId) {}

    private Learner extraLearner(World w, String tag) {
        long unique = System.nanoTime();
        return learnerRepository.save(Learner.builder()
                .fullName(tag + " Learner").learnerCode(tag + unique)
                .email(tag + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnershipRepository.findById(w.learnershipId).orElseThrow())
                .modules(new HashSet<>()).build());
    }

    private World seed() {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(
                Learnership.builder().name("FB " + unique).build());
        Category category = categoryRepository.save(
                Category.builder().categoryType("CORE").learnership(learnership).build());
        Module module = moduleRepository.save(Module.builder()
                .moduleName("FB Module").moduleCode("FB" + unique).category(category).build());
        Learner learner = learnerRepository.save(Learner.builder()
                .fullName("Feedback Learner").learnerCode("FBL" + unique)
                .email("fbl" + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>(Set.of(module))).build());
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("FB Task").description("")
                .dueDate(LocalDateTime.now().plusDays(7)).module(module).build());
        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("FB Session").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).build());
        Submission submission = submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath("lms_secure/a").originalFilename("a.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());

        return new World(learner.getId(), learner.getLearnerCode(), learner.getFullName(),
                session.getId(), submission.getId(), learnership.getId());
    }

    @Test
    @DisplayName("The console is told what is held, what is released, and who a release would notify")
    void consoleSeesReleaseState() {
        World w = seed();
        Learner other = extraLearner(w, "TWICE");
        SubmissionSession session = sessionRepository.findById(w.sessionId).orElseThrow();
        // One learner, two submissions: two held rows but one person to notify.
        Submission a = submissionRepository.save(Submission.builder()
                .learner(other).session(session).filePath("lms_secure/x").originalFilename("x.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());
        Submission b = submissionRepository.save(Submission.builder()
                .learner(other).session(session).filePath("lms_secure/y").originalFilename("y.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());
        grade(a.getId(), "FACILITATOR", "a", 60);
        grade(b.getId(), "FACILITATOR", "b", 65);

        var overview = submissionService.getSessionSubmissionsOverview(w.sessionId, null);

        assertThat(overview.getHeldCount()).isEqualTo(2);
        assertThat(overview.getReleasedCount()).isZero();
        // The confirmation the facilitator reads counts people, not submissions.
        assertThat(overview.getWouldNotifyCount()).isEqualTo(1);
        assertThat(overview.getSubmitted())
                .filteredOn(row -> row.getLearnerCode().equals(other.getLearnerCode()))
                .allSatisfy(row -> assertThat(row.isFeedbackReleased()).isFalse());
    }

    @Test
    @DisplayName("After releasing, the console shows nothing held and the rows read as released")
    void consoleSeesReleasedState() {
        World w = seed();
        grade(w.submissionId, "FACILITATOR", "Good work", 80);
        releaseService.release(w.sessionId);

        var overview = submissionService.getSessionSubmissionsOverview(w.sessionId, null);

        assertThat(overview.getHeldCount()).isZero();
        assertThat(overview.getReleasedCount()).isEqualTo(1);
        assertThat(overview.getWouldNotifyCount()).isZero();
        assertThat(overview.getSubmitted().get(0).isFeedbackReleased()).isTrue();
    }

    @Test
    @DisplayName("Unmarked and internal work is not counted as something a release would send")
    void heldCountExcludesUnmarkedAndInternal() {
        World w = seed();
        Submission s = submissionRepository.findById(w.submissionId).orElseThrow();
        s.setGradedAt(LocalDateTime.now());
        s.setFeedbackStatus(FeedbackStatus.DRAFT);
        s.setFeedbackVisibility(FeedbackVisibility.INTERNAL);
        submissionRepository.save(s);

        var overview = submissionService.getSessionSubmissionsOverview(w.sessionId, null);

        // An internal report is never going to a learner, so offering to release it would be
        // a lie about what the button does.
        assertThat(overview.getHeldCount()).isZero();
        assertThat(overview.getWouldNotifyCount()).isZero();
    }

    @Test
    @DisplayName("A learner who submitted twice is one person told, not two")
    void notifiedCountsPeopleNotSubmissions() {
        World w = seed();
        SubmissionSession session = sessionRepository.findById(w.sessionId).orElseThrow();
        Submission second = submissionRepository.save(Submission.builder()
                .learner(learnerRepository.findById(w.learnerId).orElseThrow()).session(session)
                .filePath("lms_secure/z").originalFilename("z.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());
        grade(w.submissionId, "FACILITATOR", "one", 60);
        grade(second.getId(), "FACILITATOR", "two", 70);

        FeedbackReleaseService.ReleaseResult result = releaseService.release(w.sessionId);

        // Two submissions published, one person notified. The message the facilitator reads is
        // built from notified, not published, or it would overstate who was told.
        assertThat(result.published()).isEqualTo(2);
        assertThat(result.notified()).isEqualTo(1);
    }
}
