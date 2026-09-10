package com.example.learnerassignments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Proves, on every boot, that a file uploaded through the authenticated path can be read back
 * through the authenticated path.
 *
 * <p>This exists because the failure it looks for is silent and delayed. If Cloudinary rejects
 * signed delivery for this account — as it has before, twice, recorded in the comment on
 * {@link CloudinaryService#uploadFile} — then uploads keep succeeding and rows keep being
 * written, and nothing anywhere reports a problem. The vault accepts a learner's ID document,
 * tells them it was received, and stores a row pointing at bytes nobody can fetch. That gets
 * discovered when a learner complains, or when an export comes back empty, which may be weeks
 * later and after the evidence has been deleted from their own device.
 *
 * <p>So the check writes a line on every boot, in every outcome, in the same spirit as the
 * migration and backfill completion lines: a log you can only read a state out of if it says
 * something in each state.
 *
 * <p>It does not gate anything. A transient Cloudinary problem must not take the vault down,
 * and a failure here does not mean already-stored files are unreadable — legacy public URLs
 * and files on disk are served by other paths entirely.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryHealthCheck {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CloudinaryService cloudinaryService;
    private final StoredFileService storedFileService;

    @Value("${cloudinary.health-check.enabled:true}")
    private boolean enabled;

    /**
     * Runs after startup, on its own daemon thread.
     *
     * <p>Deliberately not a {@code CommandLineRunner} like the migration and the backfill:
     * those run before the application reports itself started, and this one makes two network
     * round trips to a third party. On a free-tier instance that cold-starts in tens of
     * seconds and is health-checked by the platform, adding that to boot risks turning a
     * diagnostic into an outage.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleCheck() {
        if (!enabled) {
            log.info("Authenticated delivery health check skipped: cloudinary.health-check.enabled is false.");
            return;
        }
        if (!cloudinaryService.isConfigured()) {
            log.info("Authenticated delivery health check skipped: Cloudinary is not configured, "
                    + "so learner files are stored on local disk. On this deployment that disk is "
                    + "ephemeral — files do not survive a restart.");
            return;
        }
        Thread thread = new Thread(this::run, "delivery-health-check");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The outcome of one probe, for the admin diagnostic endpoint. The boot path ignores it
     * and reads the log instead; both come from the same run, so they cannot disagree.
     *
     * @param ok      whether a file uploaded through the authenticated path was read back intact
     * @param step    which step was reached: upload, delivery, compare, or none
     * @param detail  what happened, including the storage provider's own error where there is
     *                one. Admin-only, and never contains a signed URL.
     */
    public record Result(boolean ok, String step, String detail) {}

    /** Visible for testing. Never throws. */
    Result run() {
        String publicId = null;
        long startedAt = System.currentTimeMillis();
        try {
            // A fresh id and fresh content each run. A fixed id would let a cached copy of a
            // previous boot's bytes answer the fetch, which would report success for an upload
            // that had actually failed — and would report a spurious failure if the CDN served
            // the older copy after a successful overwrite. Neither is a signal worth having.
            byte[] probe = ("adom-lms delivery probe " + nonce()).getBytes(StandardCharsets.UTF_8);

            try {
                publicId = cloudinaryService.uploadLearnerFile(probe, "delivery-probe");
            } catch (Exception e) {
                // Storing is what failed. This one is at least loud at the point of use: a
                // learner trying to upload gets an error and knows their document did not
                // arrive. Nothing is silently accumulating.
                log.error("Authenticated delivery health check FAILED after {} ms: the file could "
                        + "not be uploaded to storage at all. Learners will see uploads fail. Files "
                        + "already stored are unaffected and still open.",
                        System.currentTimeMillis() - startedAt, e);
                return new Result(false, "upload", describe(e));
            }

            if (publicId.startsWith("http")) {
                // Would mean uploadLearnerFile had been changed back to returning a URL, which
                // is the exact regression Phase 4 exists to prevent.
                log.error("Authenticated delivery health check FAILED: the uploader returned a URL "
                        + "instead of a public_id. New learner files are being stored as publicly "
                        + "readable links.");
                return new Result(false, "upload", "The uploader returned a URL instead of a public_id.");
            }

            byte[] fetched;
            try {
                fetched = storedFileService.readBytes(publicId, "delivery probe");
            } catch (Exception e) {
                // Storing worked and reading did not. This is the dangerous one, and the one
                // this class exists for: nothing surfaces to the learner, so the vault keeps
                // accepting documents and telling people they were received.
                log.error("Authenticated delivery health check FAILED after {} ms: the file uploaded "
                        + "fine but could not be read back through a signed URL. New learner uploads "
                        + "will be accepted and stored but will not open — the vault will look like "
                        + "it is working while collecting unreadable documents. Files stored before "
                        + "authenticated delivery are unaffected and still open. Do not announce the "
                        + "Documents tab until this line reads 'passed'. public_id={}",
                        System.currentTimeMillis() - startedAt, publicId, e);
                return new Result(false, "delivery", describe(e));
            }

            if (!Arrays.equals(probe, fetched)) {
                log.error("Authenticated delivery health check FAILED: signed delivery returned {} "
                        + "bytes but they are not the bytes that were uploaded. Learner files may be "
                        + "served as the wrong content. public_id={}", fetched.length, publicId);
                return new Result(false, "compare",
                        "Delivery returned " + fetched.length + " bytes, but not the ones uploaded.");
            }

            log.info("Authenticated delivery health check passed: uploaded {} bytes and read them "
                    + "back through a signed URL in {} ms. New learner uploads are readable.",
                    probe.length, System.currentTimeMillis() - startedAt);
            return new Result(true, "complete",
                    "Uploaded " + probe.length + " bytes and read them back through a signed URL.");

        } catch (Exception e) {
            // Anything the two named steps did not already account for.
            log.error("Authenticated delivery health check FAILED after {} ms for an unexpected "
                    + "reason. Treat new learner uploads as unproven until this reads 'passed'. "
                    + "public_id={}", System.currentTimeMillis() - startedAt, publicId, e);
            return new Result(false, "unknown", describe(e));
        } finally {
            cleanUp(publicId);
        }
    }

    /**
     * The provider's own words, which are the only part of a storage failure worth reading.
     * Cloudinary says things like "Invalid Signature" or "Untrusted customer"; the wrapping
     * exception class name says nothing. Includes the cause because the SDK usually wraps.
     */
    private String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 500; t = t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" — caused by: ");
                }
                sb.append(t.getMessage());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return sb.length() == 0 ? e.getClass().getSimpleName() : sb.toString();
    }

    /**
     * Runs the same probe on demand, for the admin diagnostic endpoint.
     *
     * Fixing a storage configuration problem otherwise means a redeploy and a cold start per
     * attempt, with the answer only visible in the platform's log viewer.
     */
    public Result checkNow() {
        if (!cloudinaryService.isConfigured()) {
            return new Result(false, "configuration",
                    "Cloudinary is not configured, so learner files are stored on local disk. "
                            + "On this deployment that disk is ephemeral — files do not survive a restart.");
        }
        return run();
    }

    private void cleanUp(String publicId) {
        if (publicId == null) {
            return;
        }
        try {
            cloudinaryService.deleteLearnerFile(publicId);
        } catch (Exception e) {
            // A leftover probe file is a few bytes and harmless; failing to remove it is not
            // worth a second scary line under the one that matters.
            log.debug("Could not remove the delivery health check probe {}.", publicId, e);
        }
    }

    private String nonce() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
