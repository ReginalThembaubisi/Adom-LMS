package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.NotificationRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.repository.SubmissionSessionRepository;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Releasing a session's marking to its learners.
 *
 * Marking is a draft until this runs. The facilitator can mark a session over several sittings,
 * change their mind, and correct a mistake, without a learner watching their outcome flicker
 * between competent and not — and without the awkwardness of half a cohort having results while
 * the rest wait.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackReleaseService {

    private final SubmissionSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationRepository notificationRepository;

    /** What a release did, so the caller can tell the facilitator something true. */
    public record ReleaseResult(int published, int alreadyPublished, int notified, int unmarked) {}

    /**
     * Publishes every marked, learner-facing submission in the session.
     *
     * Unmarked submissions are left alone and counted — releasing a session before finishing it
     * is a normal mistake, and the facilitator needs to know it happened rather than be told
     * everything went out. Internal reports are never published; they are not the learner's.
     */
    @Transactional
    public ReleaseResult release(Long sessionId) {
        SubmissionSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        List<Submission> submissions = submissionRepository.findBySessionId(sessionId);
        LocalDateTime now = LocalDateTime.now();

        int published = 0;
        int alreadyPublished = 0;
        int unmarked = 0;
        Set<Long> learnersToNotify = new HashSet<>();
        List<Learner> learners = new ArrayList<>();

        for (Submission submission : submissions) {
            if (submission.getGradedAt() == null) {
                unmarked++;
                continue;
            }
            if (submission.getFeedbackVisibility() == FeedbackVisibility.INTERNAL) {
                continue;
            }
            if (submission.getFeedbackStatus() == FeedbackStatus.PUBLISHED) {
                alreadyPublished++;
                continue;
            }

            submission.setFeedbackStatus(FeedbackStatus.PUBLISHED);
            submission.setPublishedAt(now);
            submissionRepository.save(submission);
            published++;

            // One notification per learner, not one per submission: a learner with two
            // submissions in a session is told once that their marking is ready.
            if (submission.getLearner() != null && learnersToNotify.add(submission.getLearner().getId())) {
                learners.add(submission.getLearner());
            }
        }

        String sessionName = session.getSessionName();
        for (Learner learner : learners) {
            notificationRepository.save(Notification.builder()
                    .userId(learner.getId())
                    .userRole("LEARNER")
                    .type(NotificationType.FEEDBACK_RELEASED)
                    .refType("SESSION")
                    .refId(session.getId())
                    // Names this learner's own session and nobody else. No link: learners were
                    // told this system will never send them one, and a notification carrying a
                    // URL teaches them to click things that look like this.
                    .body("Your marking for " + sessionName + " is ready. Open the portal to see it.")
                    .createdAt(now)
                    .build());
        }

        log.info("Feedback released for session {} ({}): published {}, already published {}, "
                + "notified {} learner(s), {} submission(s) left unmarked.",
                session.getId(), sessionName, published, alreadyPublished, learners.size(), unmarked);

        return new ReleaseResult(published, alreadyPublished, learners.size(), unmarked);
    }
}
