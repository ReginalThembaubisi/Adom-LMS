package com.example.learnerassignments.service;

import lombok.RequiredArgsConstructor;
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
 */
@Component
@Order(70)
@RequiredArgsConstructor
public class ExportJobRecoveryRunner implements CommandLineRunner {

    private final PoeExportService exportService;

    @Override
    public void run(String... args) {
        exportService.reconcileJobsInterruptedByRestart();
        exportService.expireCompletedJobsWithMissingFiles();
    }
}
