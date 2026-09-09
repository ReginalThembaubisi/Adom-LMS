package com.example.learnerassignments.service;

import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Moves learner submissions that were stored inside the publicly-served uploads directory
 * out to the private one, rewriting their stored path as it goes.
 *
 * Closing the hole for new uploads is only half the job: every file collected before that
 * change is still sitting under the {@code /uploads/**} static resource handler, readable
 * by anyone who guesses a filename — and the names are built from the learner code and
 * session id, neither of which is secret. Those files have to move too.
 *
 * Runs on every boot and is idempotent: once a row points into the private directory there
 * is nothing left to match. It is deliberately a startup task rather than a hand-run script,
 * so a deployment that restores an older database cannot quietly leave the files exposed.
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class LegacySubmissionFileMigration implements CommandLineRunner {

    private final SubmissionRepository submissionRepository;

    @Value("${file.upload-dir:uploads}")
    private String publicUploadDir;

    @Value("${file.submission-dir:private-uploads}")
    private String privateSubmissionDir;

    @Override
    @Transactional
    public void run(String... args) {
        Path publicDir = Paths.get(publicUploadDir).toAbsolutePath().normalize();
        Path privateDir = Paths.get(privateSubmissionDir).toAbsolutePath().normalize();

        if (privateDir.startsWith(publicDir)) {
            // Nothing to do but refuse: moving files deeper inside the served tree would
            // report success while leaving every one of them publicly readable.
            log.error("file.submission-dir ({}) is inside the publicly served file.upload-dir ({}). "
                    + "Learner submissions would stay reachable without authentication. "
                    + "Migration skipped — fix the configuration.", privateDir, publicDir);
            return;
        }

        int moved = 0;
        int missing = 0;
        int scanned = 0;

        // The whole table, rather than a prefix query: stored paths have taken more than one
        // shape over the life of this code (absolute, and relative to the working directory),
        // and a cohort is a few hundred rows.
        List<Submission> submissions = submissionRepository.findAll();

        long pending = submissions.stream()
                .filter(s -> needsMove(s.getFilePath(), publicDir) || needsMove(s.getMarkedFilePath(), publicDir))
                .count();
        if (pending > 0) {
            log.warn("About to move {} learner submission file(s) out of the publicly served "
                    + "directory. This changes data, not just code: after it runs, redeploying "
                    + "an earlier build does NOT undo it — the database and the uploads "
                    + "directory have to be restored together. Each move is logged below.",
                    pending);
        }

        for (Submission submission : submissions) {
            scanned++;
            String relocated = relocate(submission.getFilePath(), publicDir, privateDir);
            if (relocated != null) {
                submission.setFilePath(relocated);
                moved++;
            } else if (needsMove(submission.getFilePath(), publicDir)) {
                missing++;
            }

            // Marked copies are uploaded to Cloudinary today, but earlier builds may have
            // written them to disk, so they are checked on the same terms.
            String relocatedMarked = relocate(submission.getMarkedFilePath(), publicDir, privateDir);
            if (relocatedMarked != null) {
                submission.setMarkedFilePath(relocatedMarked);
                moved++;
            } else if (needsMove(submission.getMarkedFilePath(), publicDir)) {
                missing++;
            }
        }

        // Always logged, including when there was nothing to do. Finding nothing and never
        // running look identical in a log that only speaks up on a change, and "never ran" is
        // the realistic failure here — a misread directory, a bean that did not start. An
        // operator checking after a deploy needs the absence of this line to mean something.
        log.info("Legacy submission file migration complete: scanned {} submission(s), "
                + "moved {} out of the public directory {} into {}, {} referenced a file "
                + "missing from disk (left untouched).",
                scanned, moved, publicDir, privateDir, missing);

        if (missing > 0) {
            log.warn("{} submission(s) point at a file that is not on disk. These were left as "
                    + "they are rather than repointed at nothing — worth investigating before "
                    + "anyone reports a missing document.", missing);
        }
    }

    /** True when this stored path is a local file sitting inside the publicly served tree. */
    private boolean needsMove(String storedPath, Path publicDir) {
        if (storedPath == null || storedPath.isBlank()) {
            return false;
        }
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
            return false;
        }
        return resolve(storedPath).startsWith(publicDir);
    }

    /**
     * Moves one file into the private directory, returning its new stored path — or null if
     * it did not need moving, or the file is gone, or the move failed.
     */
    private String relocate(String storedPath, Path publicDir, Path privateDir) {
        if (!needsMove(storedPath, publicDir)) {
            return null;
        }

        Path source = resolve(storedPath);
        if (!Files.isRegularFile(source)) {
            log.warn("Submission file {} is recorded in the database but missing from disk; "
                    + "leaving the row as it is.", source);
            return null;
        }

        try {
            Files.createDirectories(privateDir);
            Path target = availableTarget(privateDir, source.getFileName().toString());
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            // One line per file, because this is the only record of what moved where. Rolling
            // the code back does not put these files back: redeploying the old jar leaves it
            // looking in the public directory for files that are no longer in it, against rows
            // that point somewhere it does not know about. Reversing this means moving the
            // files back and restoring the paths, and that needs a list.
            log.info("Moved submission file out of the public directory: {} -> {}", source, target);
            return target.toString();
        } catch (IOException e) {
            // A file that could not be moved is still exposed, so this is loud rather than
            // silent — but it must not stop the rest of the batch, or one bad file leaves
            // every later one where it was.
            log.error("Could not move submission file {} out of the public uploads directory. "
                    + "It remains publicly readable until this is resolved.", source, e);
            return null;
        }
    }

    /** Resolves a stored path, which may be absolute or relative to the working directory. */
    private Path resolve(String storedPath) {
        return Paths.get(storedPath).toAbsolutePath().normalize();
    }

    /** A free filename in the target directory, so a collision never overwrites evidence. */
    private Path availableTarget(Path directory, String filename) {
        Path candidate = directory.resolve(filename);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(suffix + "_" + filename);
            suffix++;
        }
        return candidate;
    }
}
