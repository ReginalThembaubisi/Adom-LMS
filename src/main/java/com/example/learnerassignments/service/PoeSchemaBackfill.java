package com.example.learnerassignments.service;

import com.example.learnerassignments.model.FeedbackStatus;
import com.example.learnerassignments.model.FeedbackVisibility;
import com.example.learnerassignments.model.ModuleFile;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.repository.ModuleFileRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fills in the Portfolio of Evidence columns on rows that predate them.
 *
 * This writes to production data, so it is built the same way LegacySubmissionFileMigration
 * was: it says what it intends to change before changing it, it says what it did on every
 * run including the run where it does nothing, and running it twice changes nothing the
 * second time.
 *
 * Idempotence comes from the shape of the work rather than from a flag: only null columns are
 * written, so once a row is filled it no longer matches. That survives a restore from backup,
 * a rolled-back deploy, and the Free instance waking up several times an hour.
 *
 * It does not compute hashes for existing files. Doing so would mean re-downloading every
 * submission from Cloudinary to hash it, and a hash of a file fetched now proves only what
 * that file is now.
 */
@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class PoeSchemaBackfill implements CommandLineRunner {

    private final SubmissionRepository submissionRepository;
    private final ModuleFileRepository moduleFileRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Submission> submissions = submissionRepository.findAll();
        List<ModuleFile> moduleFiles = moduleFileRepository.findAll();

        long submissionsPending = submissions.stream().filter(this::needsBackfill).count();
        long moduleFilesPending = moduleFiles.stream().filter(this::needsBackfill).count();

        if (submissionsPending > 0 || moduleFilesPending > 0) {
            log.warn("Portfolio of Evidence backfill will set values on {} submission(s) and {} "
                    + "module file(s) that predate these columns. This is a data change: rolling "
                    + "back to an earlier build leaves the written values in place, harmlessly, "
                    + "but restoring the database is the only way to undo it.",
                    submissionsPending, moduleFilesPending);
        }

        int submissionsPublished = 0;
        int submissionsDrafted = 0;
        int visibilitySet = 0;

        for (Submission submission : submissions) {
            if (submission.getFeedbackStatus() == null) {
                // Anything already marked was, by definition, already visible to the learner
                // before this column existed — calling it a draft now would retract feedback
                // they have already read. Everything else has nothing to publish.
                boolean alreadyMarked = submission.getMarkedFilePath() != null
                        || (submission.getFeedback() != null && !submission.getFeedback().isBlank());
                submission.setFeedbackStatus(alreadyMarked ? FeedbackStatus.PUBLISHED : FeedbackStatus.DRAFT);
                if (alreadyMarked) {
                    submissionsPublished++;
                } else {
                    submissionsDrafted++;
                }
            }
            if (submission.getFeedbackVisibility() == null) {
                submission.setFeedbackVisibility(FeedbackVisibility.LEARNER);
                visibilitySet++;
            }
        }

        int moduleFilesFilled = 0;
        for (ModuleFile moduleFile : moduleFiles) {
            boolean changed = false;
            if (moduleFile.getPoeSection() == null) {
                // Facilitator guides are section 3 of the SETA folder structure.
                moduleFile.setPoeSection(3);
                changed = true;
            }
            if (moduleFile.getVersion() == null) {
                moduleFile.setVersion(1);
                changed = true;
            }
            if (moduleFile.getCurrent() == null) {
                moduleFile.setCurrent(true);
                changed = true;
            }
            if (changed) {
                moduleFilesFilled++;
            }
        }

        // Logged on every run, including the one that changes nothing. A backfill that only
        // speaks up when it works cannot be distinguished, afterwards, from one that never ran.
        log.info("Portfolio of Evidence backfill complete: scanned {} submission(s) and {} module "
                + "file(s); marked {} as PUBLISHED and {} as DRAFT, set visibility on {}, and "
                + "filled section/version/current on {} module file(s). Hashes are computed at "
                + "upload and are not backfilled.",
                submissions.size(), moduleFiles.size(),
                submissionsPublished, submissionsDrafted, visibilitySet, moduleFilesFilled);
    }

    private boolean needsBackfill(Submission submission) {
        return submission.getFeedbackStatus() == null || submission.getFeedbackVisibility() == null;
    }

    private boolean needsBackfill(ModuleFile moduleFile) {
        return moduleFile.getPoeSection() == null
                || moduleFile.getVersion() == null
                || moduleFile.getCurrent() == null;
    }
}
