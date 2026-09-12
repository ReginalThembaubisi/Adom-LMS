package com.example.learnerassignments.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs {@link PoeExportService#reconcileJobsInterruptedByRestart()} once per boot.
 *
 * A PoE export job is either QUEUED (created, not yet dispatched) or RUNNING (a worker is
 * fetching files and building the zip) right up until it finishes — and nothing about either
 * state is durable across a restart. Found in production on a free-tier instance that spun down
 * mid-export: two jobs sat QUEUED forever, indistinguishable from "about to start" to anything
 * polling them, because nothing ever told the row its worker was gone. This runs after every
 * boot specifically to close that gap, on the same footing as {@link LegacySubmissionFileMigration}
 * and the other startup reconcilers in this package — idempotent (a job already FAILED or
 * COMPLETED is untouched) and cheap enough to run every time rather than once.
 */
@Component
@Order(70)
@RequiredArgsConstructor
public class ExportJobRecoveryRunner implements CommandLineRunner {

    private final PoeExportService exportService;

    @Override
    public void run(String... args) {
        exportService.reconcileJobsInterruptedByRestart();
    }
}
