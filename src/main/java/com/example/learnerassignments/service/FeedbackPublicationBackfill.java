package com.example.learnerassignments.service;

import com.example.learnerassignments.model.FeedbackStatus;
import com.example.learnerassignments.model.FeedbackVisibility;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.model.SystemSetting;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 * <p><strong>Runs exactly once, ever — gated on a completion marker, not on the data.</strong>
 * The first version of this class judged whether there was work to do purely from the shape of
 * a row: graded, not yet published, not internal. That is indistinguishable from a submission
 * graded five minutes ago through the real marking screen — {@code gradeSubmission()} sets
 * {@code gradedAt} and {@code DRAFT} together on every grading action, forever, by design. A
 * data-shaped condition therefore never stops matching, and this ran on every boot: on a
 * free-tier instance that spins down when idle, "every boot" can mean hours after a facilitator
 * marked something and deliberately left it held, with no deploy in between. Phase 5's whole
 * point — release is a decision, not a default — was silently undone by its own backfill.
 *
 * <p>A date cutoff has the same ambiguity by another route: it has to be right, forever, on
 * every environment this ever runs in, and a row graded one second after the cutoff looks
 * identical to one graded one second before it. A completion marker sidesteps the question
 * entirely — it does not ask "is this row old enough", it asks "has this task already run",
 * which is a fact about the task rather than a guess about any one row. It is a row in {@code
 * system_settings}, the same durable key/value table {@link
 * com.example.learnerassignments.controller.AdminController}'s registration-status toggle
 * already trusts to survive a restart, and it is on {@link BackupService}'s table list
 * specifically so it survives a restore too — a restore that dropped it would make this look
 * like a fresh install and run the sweep again, reproducing the exact bug this exists to close,
 * just triggered by "restore" instead of "boot".
 */
@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class FeedbackPublicationBackfill implements CommandLineRunner {

    /**
     * Package-visible so {@link BackupService} and tests can both refer to the same literal
     * without one drifting from the other.
     */
    static final String COMPLETION_MARKER_KEY = "POE_FEEDBACK_PUBLICATION_BACKFILL_DONE";

    private final SubmissionRepository submissionRepository;
    private final SystemSettingRepository systemSettingRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (systemSettingRepository.existsById(COMPLETION_MARKER_KEY)) {
            // Always logged, no-op included: a run that finds the marker and a run that never
            // happened look identical from the outside unless this says which one it was.
            log.info("Feedback publication backfill complete: already ran (marker present); "
                    + "no submissions scanned, nothing published.");
            return;
        }

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
                    + "Leaving them as drafts would retract feedback that has been read. This is "
                    + "the only time this will run — a completion marker is written below.",
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

        // Written in the same transaction as the publishes above, so the two can only land
        // together: a failure partway through leaves neither the data change nor the marker
        // committed, and the next boot retries the whole sweep from scratch rather than being
        // left half-done with no record of it.
        systemSettingRepository.save(SystemSetting.builder()
                .settingKey(COMPLETION_MARKER_KEY)
                .settingValue(LocalDateTime.now().toString())
                .build());

        // Always logged, no-op included, for the same reason as the migration before it:
        // finding nothing and never running look identical in a log that only speaks up on a
        // change, and "never ran" is the failure that matters here.
        log.info("Feedback publication backfill complete: scanned {} submission(s), published {} "
                + "that were already visible, left {} internal report(s) unpublished. Marker "
                + "written; this will not run again.",
                submissions.size(), toPublish.size(), internalLeftAlone);
    }
}
