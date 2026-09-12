package com.example.learnerassignments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs {@link PoeExportService}'s two boot-time export reconciliations once per boot.
 *
 * A PoE export job is either QUEUED (created, not yet dispatched), RUNNING (a worker is
 * fetching files and building the zip), or — once finished — COMPLETED with a file sitting on
 * this deployment's disk. None of that survives a restart: there is no persistent disk on this
 * plan, and the free instance spins down when idle, so a restart can land mid-job or well after
 * one finished. Found in production both ways: two jobs sat QUEUED forever, indistinguishable
 * from "about to start" to anything polling them; and a COMPLETED job can just as easily have
 * its file vanish before anyone gets to download it, which is worse than a clean failure — the
 * row still claims success right up until the download 404s.
 *
 * <p>{@link PoeExportService#reconcileJobsInterruptedByRestart()} handles the first case,
 * {@link PoeExportService#expireCompletedJobsWithMissingFiles()} the second. Both run on the
 * same footing as {@link LegacySubmissionFileMigration} and the other startup reconcilers in
 * this package — idempotent and cheap enough to run every time rather than once.
 *
 * <p><strong>Caught here too, deliberately, on top of each method's own per-row isolation.</strong>
 * An uncaught exception from a {@link CommandLineRunner} fails the whole application boot — found
 * in production the hard way: adding {@code ExportJobStatus.EXPIRED} without widening the
 * database's existing {@code CHECK} constraint on {@code export_jobs.status} (the same
 * ddl-auto=update trap {@code NotificationType} hit before it) meant the very first boot after
 * that deploy failed the write, and with nothing catching it here, took the entire site down
 * rather than just leaving one row unreconciled. A boot-time cleanup task is a nice-to-have; it
 * must never be able to cost more than the problem it exists to fix, which for this runner is
 * "the whole application does not start" — the single worst outcome a background reconciliation
 * could possibly have. Each method already isolates one bad row from the rest of its own loop;
 * this catch is what stops a failure neither of them anticipated from reaching {@code main()}.
 */
@Component
@Order(70)
@RequiredArgsConstructor
@Slf4j
public class ExportJobRecoveryRunner implements CommandLineRunner {

    private final PoeExportService exportService;

    @Override
    public void run(String... args) {
        try {
            exportService.reconcileJobsInterruptedByRestart();
        } catch (Exception e) {
            log.error("PoE export recovery: reconciling QUEUED/RUNNING jobs failed. The "
                    + "application will keep starting; those jobs stay as they are until the "
                    + "next boot.", e);
        }
        try {
            exportService.expireCompletedJobsWithMissingFiles();
        } catch (Exception e) {
            log.error("PoE export recovery: expiring completed jobs with missing files failed. "
                    + "The application will keep starting; those jobs stay COMPLETED until the "
                    + "next boot.", e);
        }
    }
}
