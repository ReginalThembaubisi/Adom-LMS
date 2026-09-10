package com.example.learnerassignments.service;

import com.example.learnerassignments.model.FeedbackStatus;
import com.example.learnerassignments.model.FeedbackVisibility;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Publishes marking that was already visible before this phase existed.
 *
 * Until Phase 5, nothing read feedback_status: every marked submission showed its outcome,
 * marks and comment to the learner regardless of what that column said. From Phase 5 a draft
 * is hidden — so without this, deploying Phase 5 would take feedback away from learners who
 * have already read it, and from a facilitator's point of view marking they had finished would
 * silently un-happen.
 *
 * Phase 2's backfill tried to prevent exactly this, and mostly did, but it decided by looking
 * for a rasterized marked copy or a non-empty comment. That misses two real cases: work graded
 * with an outcome and marks but no written comment, and everything marked after that backfill
 * ran, which takes the entity default of DRAFT. Both were visible to the learner yesterday.
 *
 * So the test here is {@code gradedAt != null} — the honest record that marking happened, and
 * therefore that the learner could see it. Internal reports are left alone: they were never the
 * learner's, and publishing them is the one thing this must not do.
 *
 * Idempotent: it only moves a graded row from DRAFT to PUBLISHED, so a second run finds
 * nothing. Once Phase 5 has been deployed the population it can act on stops growing, because
 * everything marked from then on is a draft by intent rather than by accident.
 */
@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class FeedbackPublicationBackfill implements CommandLineRunner {

    private final SubmissionRepository submissionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Submission> submissions = submissionRepository.findAll();

        List<Submission> toPublish = submissions.stream()
                .filter(s -> s.getGradedAt() != null)
                .filter(s -> s.getFeedbackStatus() != FeedbackStatus.PUBLISHED)
                .filter(s -> s.getFeedbackVisibility() != FeedbackVisibility.INTERNAL)
                .toList();

        long internalLeftAlone = submissions.stream()
                .filter(s -> s.getGradedAt() != null)
                .filter(s -> s.getFeedbackVisibility() == FeedbackVisibility.INTERNAL)
                .count();

        if (!toPublish.isEmpty()) {
            log.warn("About to publish {} submission(s) whose marking the learner could already "
                    + "see before feedback could be held back. This changes data, not just code. "
                    + "Leaving them as drafts would retract feedback that has been read.",
                    toPublish.size());

            toPublish.forEach(s -> {
                s.setFeedbackStatus(FeedbackStatus.PUBLISHED);
                if (s.getPublishedAt() == null) {
                    // The date it was marked, not today: it became visible when it was graded,
                    // and stamping today would misdate every historical release.
                    s.setPublishedAt(s.getGradedAt());
                }
                submissionRepository.save(s);
            });
        }

        // Always logged, no-op included, for the same reason as the migration before it:
        // finding nothing and never running look identical in a log that only speaks up on a
        // change, and "never ran" is the failure that matters here.
        log.info("Feedback publication backfill complete: scanned {} submission(s), published {} "
                + "that were already visible, left {} internal report(s) unpublished.",
                submissions.size(), toPublish.size(), internalLeftAlone);
    }
}
