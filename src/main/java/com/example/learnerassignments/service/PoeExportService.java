package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.PoeExportDtos.CreateExportRequest;
import com.example.learnerassignments.dto.PoeExportDtos.ExportJobResponse;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.security.StaffPrincipal;
import com.example.learnerassignments.security.StaffPrincipal.StaffRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a SETA portfolio export: a zip laid out exactly like the folder structure section 2 of
 * the implementation brief describes, plus an index and a signatures manifest.
 *
 * <p>Two rules run through the whole class.
 *
 * <p><strong>Nothing here trusts the request thread.</strong> {@link #createJob} runs
 * synchronously and only validates and records intent; the actual fetching and zipping happens
 * on {@link #runExportAsync}, an {@code @Async} worker with no HTTP request bound to it — which
 * is why scope is resolved through {@link ScopeService} (identity as a parameter, never
 * {@code SecurityContextHolder}) rather than anything request-scoped.
 *
 * <p><strong>One bad file must not sink the whole job.</strong> A 600-file learnership export
 * that aborts on the first unreadable row would be worse than useless — it would report
 * failure for 599 learners whose evidence was perfectly fine. Every file fetch is wrapped and a
 * failure is skipped, counted, and logged; only a failure in the zip stream itself (out of
 * disk, for instance) is allowed to fail the job, because at that point nothing further can be
 * written regardless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoeExportService {

    private final ExportJobRepository exportJobRepository;
    private final ScopeService scopeService;
    private final LearnerRepository learnerRepository;
    private final LearnershipRepository learnershipRepository;
    private final CategoryRepository categoryRepository;
    private final ModuleRepository moduleRepository;
    private final ModuleFileRepository moduleFileRepository;
    private final LearnerDocumentRepository documentRepository;
    private final SubmissionRepository submissionRepository;
    private final StoredFileService storedFileService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final AdminRepository adminRepository;
    private final AssessorRepository assessorRepository;
    private final ModeratorRepository moderatorRepository;
    private final ObjectMapper objectMapper;

    /**
     * A self-reference to this bean's Spring proxy, {@code @Lazy} to break the construction
     * cycle that would otherwise create.
     *
     * <p>{@code runExportAsync} calling {@code runExport} as a plain {@code this.runExport(...)}
     * would be a same-class self-invocation, which bypasses the proxy entirely — and with it,
     * every annotation the proxy is what applies. {@code @Transactional} on {@code runExport}
     * would simply not run, which is not a theoretical concern: it is exactly what happened the
     * first time this went through a real container rather than a test that calls
     * {@code runExport} directly. Routing the call through {@code self} instead calls the proxy,
     * which is what makes {@code @Transactional} apply at all.
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private PoeExportService self;

    @Value("${file.submission-dir:private-uploads}")
    private String privateDir;

    // Package-private (not private): PoePortfolioBrowserService reads these same maps so an
    // admin browsing a learner on screen sees the identical section/category names the zip
    // would use — one map, never two that could drift apart.
    static final Map<Integer, String> SECTION_FOLDERS = Map.of(
            1, "1. PERSONAL DETAILS",
            2, "2. QUALIFICATION DETAILS",
            3, "3. ASSESSMENT GUIDELINES",
            4, "4. ASSESSMENT ACTIVITIES",
            5, "5. FEEDBACK",
            6, "6. ADDITIONAL EVIDENCE"
    );

    static final Map<String, String> CATEGORY_FOLDERS = Map.of(
            "FUNDAMENTAL", "Fundamentals",
            "CORE", "Cores",
            "ELECTIVE", "Electives"
    );

    // ================================================================== creation & validation

    /**
     * Validates the request, resolves who is in scope, and records the job. Returns
     * immediately — the caller gets something to poll before a single file has been fetched.
     *
     * <p>Role gates, by scope type:
     * <ul>
     *   <li>LEARNER — any staff role, but only for a learner {@link ScopeService} already
     *       lets them reach; anyone else answers 404, the same as reading that learner any
     *       other way, so this endpoint cannot be used to confirm a learner id exists.</li>
     *   <li>COHORT, LEARNERSHIP, SECTION — admin only. These are the qualification-wide,
     *       SETA-facing scopes; the brief is explicit that this is an admin function.</li>
     *   <li>MODERATION_SAMPLE — an assessor or moderator gets their own reach, resolved through
     *       {@link ScopeService#accessibleLearnerIds} exactly as their normal reads are, so
     *       "an assessor's export contains only their assigned learners" holds by construction.
     *       An admin may request one on a named assessor's or moderator's behalf, but may not
     *       substitute a different identity for a non-admin caller's own request.</li>
     * </ul>
     */
    @Transactional
    public ExportJob createJob(StaffPrincipal principal, CreateExportRequest request) {
        ExportScopeType scopeType = parseScopeType(request.getScopeType());
        Map<String, Object> scopeRef = new LinkedHashMap<>();
        String scopeLabel;
        List<Long> learnerIds;

        switch (scopeType) {
            case LEARNER -> {
                if (request.getLearnerId() == null) {
                    throw new IllegalArgumentException("learnerId is required for a learner export.");
                }
                Learner learner = learnerRepository.findById(request.getLearnerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Learner not found"));
                if (!principal.isAdmin() && !scopeService.canAccessLearner(principal, learner.getId())) {
                    // Answers the same as a learner that does not exist. A 403 here would
                    // confirm the id is real, which is exactly the probe this system's
                    // 404-not-403 rule exists to close everywhere else.
                    throw new ResourceNotFoundException("Learner not found");
                }
                scopeRef.put("learnerId", learner.getId());
                scopeLabel = learner.getFullName() + " (" + learner.getLearnerCode() + ")";
                learnerIds = List.of(learner.getId());
            }
            case COHORT -> {
                requireAdmin(principal, "cohort");
                Learnership learnership = requireLearnership(request.getLearnershipId());
                String cohort = requireNonBlank(request.getCohort(), "cohort is required for a cohort export.");
                scopeRef.put("learnershipId", learnership.getId());
                scopeRef.put("cohort", cohort);
                scopeLabel = learnership.getName() + " - " + cohort;
                learnerIds = learnerRepository.findByLearnership_IdAndCohort(learnership.getId(), cohort)
                        .stream().map(Learner::getId).toList();
            }
            case LEARNERSHIP -> {
                requireAdmin(principal, "learnership");
                Learnership learnership = requireLearnership(request.getLearnershipId());
                scopeRef.put("learnershipId", learnership.getId());
                scopeLabel = learnership.getName();
                learnerIds = learnerRepository.findByLearnership_Id(learnership.getId())
                        .stream().map(Learner::getId).toList();
            }
            case SECTION -> {
                requireAdmin(principal, "section");
                Learnership learnership = requireLearnership(request.getLearnershipId());
                if (request.getCategoryId() == null) {
                    throw new IllegalArgumentException("categoryId is required for a section export.");
                }
                Category category = categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
                if (category.getLearnership() == null || !learnership.getId().equals(category.getLearnership().getId())) {
                    throw new ResourceNotFoundException("Category not found");
                }
                scopeRef.put("learnershipId", learnership.getId());
                scopeRef.put("categoryId", category.getId());
                scopeLabel = learnership.getName() + " - " + categoryFolder(category.getCategoryType()) + " only";
                learnerIds = learnerRepository.findByLearnership_Id(learnership.getId())
                        .stream().map(Learner::getId).toList();
            }
            case MODERATION_SAMPLE -> {
                StaffPrincipal target;
                if (principal.isAdmin()) {
                    String staffRole = requireNonBlank(request.getStaffRole(),
                            "staffRole is required when an admin requests a moderation-sample export.");
                    if (request.getStaffId() == null) {
                        throw new IllegalArgumentException(
                                "staffId is required when an admin requests a moderation-sample export.");
                    }
                    target = resolveStaff(staffRole, request.getStaffId());
                } else if (principal.role() == StaffRole.ASSESSOR || principal.role() == StaffRole.MODERATOR) {
                    // Always self. A caller cannot name someone else's id here — that would be
                    // one assessor pulling another's assigned learners' documents.
                    target = principal;
                } else {
                    throw new AccessDeniedException(
                            "Only an assessor, a moderator, or an admin acting on their behalf may request this export.");
                }
                scopeRef.put("staffRole", target.role().name());
                scopeRef.put("staffId", target.id());
                scopeLabel = target.role().name() + " " + staffDisplayName(target) + "’s assigned learners";
                learnerIds = new ArrayList<>(scopeService.accessibleLearnerIds(target));
            }
            default -> throw new IllegalArgumentException("Unknown export scope: " + scopeType);
        }

        ExportJob job = ExportJob.builder()
                .requestedById(principal.id())
                .requestedByRole(principal.role().name())
                .scopeType(scopeType)
                .scopeRef(writeJson(scopeRef))
                .scopeLabel(scopeLabel)
                .status(ExportJobStatus.QUEUED)
                .learnerCount(learnerIds.size())
                .build();
        ExportJob saved = exportJobRepository.save(job);
        log.info("PoE export {} queued by {} {}: scope={} label=\"{}\" learners={}",
                saved.getId(), principal.role(), principal.id(), scopeType, scopeLabel, learnerIds.size());
        return saved;
    }

    // ================================================================== listing & lookup

    @Transactional(readOnly = true)
    public List<ExportJob> listVisibleTo(StaffPrincipal principal) {
        if (principal.isAdmin()) {
            return exportJobRepository.findAllByOrderByCreatedAtDesc();
        }
        return exportJobRepository.findByRequestedByRoleAndRequestedByIdOrderByCreatedAtDesc(
                principal.role().name(), principal.id());
    }

    /** A job, but only if this identity may see it. Answers 404 rather than 403, as elsewhere. */
    @Transactional(readOnly = true)
    public ExportJob requireVisible(Long jobId, StaffPrincipal principal) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export not found"));
        boolean owner = principal.role().name().equals(job.getRequestedByRole())
                && principal.id().equals(job.getRequestedById());
        if (!principal.isAdmin() && !owner) {
            throw new ResourceNotFoundException("Export not found");
        }
        return job;
    }

    public ExportJobResponse toResponse(ExportJob job) {
        return ExportJobResponse.builder()
                .id(job.getId())
                .scopeType(job.getScopeType().name())
                .scopeLabel(job.getScopeLabel())
                .status(job.getStatus().name())
                .learnerCount(job.getLearnerCount())
                .fileCount(job.getFileCount())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .error(job.getError())
                .downloadAvailable(job.getStatus() == ExportJobStatus.COMPLETED
                        && job.getResultPublicId() != null && !job.getResultPublicId().isBlank())
                .build();
    }

    // ================================================================== boot-time recovery

    /**
     * Marks every job left QUEUED or RUNNING as FAILED. Called once per boot by
     * {@link ExportJobRecoveryRunner} — see that class's doc for why.
     *
     * <p>Both states mean "a worker is or will be working on this": QUEUED between
     * {@link #createJob} returning and {@link #runExportAsync} being picked up, RUNNING while
     * that worker fetches and zips. Neither can survive the process restarting — dispatch is a
     * plain {@code @Async} method call, not a durable queue, so a QUEUED job's dispatch simply
     * never happens if the process that would have made it restarts first, and a RUNNING job's
     * worker thread is just gone. A job found in either state at startup is proof the process
     * that was going to finish it no longer exists; found in production on a free-tier instance
     * that spins down mid-job, leaving rows an admin's poll reported as "processing" forever,
     * with nothing ever telling them a retry was needed.
     */
    @Transactional
    public int reconcileJobsInterruptedByRestart() {
        List<ExportJob> stuck = exportJobRepository.findByStatusIn(
                List.of(ExportJobStatus.QUEUED, ExportJobStatus.RUNNING));
        for (ExportJob job : stuck) {
            ExportJobStatus previousStatus = job.getStatus();
            job.setStatus(ExportJobStatus.FAILED);
            job.setError("The application restarted before this export finished, and it cannot "
                    + "still be running. Request the export again.");
            job.setCompletedAt(LocalDateTime.now());
            exportJobRepository.save(job);
            auditLogService.log(job.getRequestedByRole(), requesterUsername(job), "POE_EXPORT_FAILED",
                    "ExportJob", job.getId(),
                    "Marked failed on boot: was " + previousStatus + " when the application last stopped.");
        }
        if (!stuck.isEmpty()) {
            log.warn("PoE export recovery: {} job(s) left QUEUED/RUNNING from before this boot "
                    + "have been marked FAILED.", stuck.size());
        }
        return stuck.size();
    }

    // ================================================================== the worker

    /**
     * The real entry point for the controller: dispatches onto the export executor and
     * returns immediately, with no HTTP request bound to the thread that does the work.
     *
     * <p>Split from {@link #runExport} so a test can call the synchronous method directly, in
     * the same thread and the same transaction as its fixture, rather than fighting the
     * classic trap of an {@code @Async} worker reading a database connection that cannot yet
     * see the calling test's uncommitted setup. No other test in this codebase reaches across
     * that boundary in the first place, for the same reason.
     */
    @Async("exportTaskExecutor")
    public void runExportAsync(Long jobId) {
        self.runExport(jobId);
    }

    @Transactional
    public void runExport(Long jobId) {
        ExportJob job = exportJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("PoE export {} vanished before the worker could start it.", jobId);
            return;
        }

        // Named so a failure can say what it was doing, not just that it failed. A job stuck
        // at RUNNING with no reason is the state an admin cannot act on: they cannot tell a
        // retry is worth trying from one that will fail identically.
        String stage = "starting the export";
        Path tempZip = null;
        try {
            job.setStatus(ExportJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
            exportJobRepository.save(job);

            stage = "resolving who is in scope";
            List<Learner> learners = resolveLearnerIds(job).stream()
                    .map(id -> learnerRepository.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Learner::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();

            stage = "building the export bundle";
            tempZip = Files.createTempFile("poe-export-" + jobId + "-", ".zip");
            Counters counters = new Counters();
            List<IndexEntry> indexEntries = new ArrayList<>();
            Long sectionOnlyCategoryId = job.getScopeType() == ExportScopeType.SECTION
                    ? asLong(readJson(job.getScopeRef()).get("categoryId")) : null;

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tempZip.toFile())))) {
                for (Learner learner : learners) {
                    writeLearnerFolder(zos, learner, sectionOnlyCategoryId, indexEntries, counters);
                }
                writeIndexPdf(zos, job, learners, indexEntries, counters);
                writeSignaturesCsv(zos);
            }

            stage = "storing the finished export";
            String resultId = store(tempZip, jobId);
            // store() always moves the temp file to its resting place — see its own doc for why
            // this bundle never goes to Cloudinary — so there is nothing left at tempZip for the
            // finally block below to clean up.
            tempZip = null;

            job.setStatus(ExportJobStatus.COMPLETED);
            job.setLearnerCount(learners.size());
            job.setFileCount(counters.filesWritten);
            job.setResultPublicId(resultId);
            job.setCompletedAt(LocalDateTime.now());
            exportJobRepository.save(job);

            auditLogService.log(job.getRequestedByRole(), requesterUsername(job), "POE_EXPORT_COMPLETED",
                    "ExportJob", job.getId(), String.format(
                            "scope=%s label=\"%s\" learners=%d filesWritten=%d filesSkipped=%d",
                            job.getScopeType(), job.getScopeLabel(), learners.size(),
                            counters.filesWritten, counters.filesSkipped));

            log.info("PoE export {} complete: {} learner(s), {} file(s) written, {} skipped.",
                    job.getId(), learners.size(), counters.filesWritten, counters.filesSkipped);

            notifyRequester(job);
        } catch (Exception e) {
            log.error("PoE export {} failed while {}.", jobId, stage, e);
            job.setStatus(ExportJobStatus.FAILED);
            job.setError(safeErrorMessage(stage, e));
            job.setCompletedAt(LocalDateTime.now());
            exportJobRepository.save(job);
            auditLogService.log(job.getRequestedByRole(), requesterUsername(job), "POE_EXPORT_FAILED",
                    "ExportJob", job.getId(), job.getError());
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                    // Leaked temp files are cleaned up by the OS eventually; not worth failing
                    // an already-finished job over.
                }
            }
        }
    }

    /**
     * Moves the finished zip into the same private, non-statically-served directory the learner
     * document vault falls back to when Cloudinary is not configured — unconditionally, even
     * when Cloudinary is configured for everything else this application stores. Read back
     * through {@link StoredFileService} exactly like any other local-disk file, so the download
     * endpoint needs no export-specific storage logic.
     *
     * <p>This used to upload to Cloudinary like any other stored file, the same as
     * {@link CloudinaryService#uploadLearnerFile(java.io.File, String)} is still used for. Two
     * things about a whole-cohort export make that the wrong call specifically for this one file
     * type, both of which surfaced in production as a job that built its zip correctly and then
     * failed only while storing it:
     * <ul>
     *   <li><strong>Size.</strong> An individual learner document is a few megabytes; a
     *       learnership-wide zip can run into the hundreds, and the plan this account is on
     *       enforces a file-size ceiling that a cohort export can exceed while a single document
     *       never does.</li>
     *   <li><strong>The upload type itself.</strong> This account has a documented history of
     *       {@code type: "authenticated"} uploads failing outright — precisely the upload type
     *       {@code uploadLearnerFile} always requests, for the reasons recorded on that
     *       method.</li>
     * </ul>
     *
     * <p>Storing straight to local disk sidesteps both: no size ceiling this application does
     * not itself impose, no upload type to fail on, and no network round trip an admin has to
     * wait through twice — once to store it, once to download it — for a file nobody but that
     * admin will ever fetch. The trade-off is the same one every other local-disk fallback in
     * this codebase already accepts: on an ephemeral instance the file does not survive a
     * restart. An export is meant to be downloaded within minutes of completing, not archived
     * here; a job row surviving a restart the underlying file does not is what
     * {@code ExportJobRecoveryRunner} exists to reconcile.
     */
    private String store(Path tempZip, Long jobId) throws IOException {
        Path dir = Paths.get(privateDir, "exports").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path dest = dir.resolve("export_" + jobId + "_" + System.currentTimeMillis() + ".zip");
        Files.move(tempZip, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toString();
    }

    // ================================================================== zip content

    private void writeLearnerFolder(ZipOutputStream zos, Learner learner, Long sectionOnlyCategoryId,
                                     List<IndexEntry> indexEntries, Counters counters) throws IOException {
        String folder = sanitizeSegment(learner.getFullName() + " (" + learner.getLearnerCode() + ")") + "/";
        LearnerPortfolio portfolio = resolvePortfolio(learner, sectionOnlyCategoryId);

        for (LearnerDocument doc : portfolio.personalDocuments()) {
            if (doc.getDocumentType() == null) {
                continue;
            }
            String sectionFolder = SECTION_FOLDERS.getOrDefault(doc.getDocumentType().getPoeSection(), SECTION_FOLDERS.get(6));
            String entryName = folder + sectionFolder + "/" + canonicalDocumentFilename(learner, doc);
            writeStoredFile(zos, entryName, doc.getFilePath(), "document", counters);
            indexEntries.add(new IndexEntry(learner, sectionFolder,
                    doc.getDocumentType().getLabel() + " v" + doc.getVersion(), doc.getUploadedAt()));
        }

        for (ModulePortfolioFolder mf : portfolio.moduleFolders()) {
            Module module = mf.module();
            String moduleBase = folder + "%s/" + mf.categoryFolder() + "/" + sanitizeSegment(module.getModuleName()) + "/";

            if (mf.guide() != null) {
                String entryName = sectionPath(moduleBase, 3) + canonicalGuideFilename(module, mf.guide());
                writeStoredFile(zos, entryName, mf.guide().getFilePath(), "facilitator guide", counters);
                indexEntries.add(new IndexEntry(learner, SECTION_FOLDERS.get(3),
                        module.getModuleName() + " guide v" + mf.guide().getVersion(), mf.guide().getCreatedAt()));
            }

            for (SubmissionOnModule som : mf.submissions()) {
                Submission submission = som.submission();
                String sessionName = som.sessionName();
                int ordinal = som.ordinal();

                String activityEntry = sectionPath(moduleBase, 4) + canonicalSubmissionFilename(learner, sessionName, ordinal, submission);
                writeStoredFile(zos, activityEntry, submission.getFilePath(), "submission", counters);
                indexEntries.add(new IndexEntry(learner, SECTION_FOLDERS.get(4),
                        sessionName + " v" + ordinal, submission.getSubmittedAt()));

                if (submission.getGradedAt() != null) {
                    String feedbackEntry = sectionPath(moduleBase, 5) + canonicalFeedbackFilename(learner, sessionName, ordinal);
                    writeBytes(zos, feedbackEntry, renderFeedback(submission).getBytes(StandardCharsets.UTF_8), counters);
                    // DRAFT is included on purpose — see renderFeedback's note — but the index
                    // is the one place a verifier scans every row without opening each file, so
                    // the status has to be visible there too, not only inside the .txt itself.
                    // Carried as its own tag rather than folded into the label: a label can be
                    // truncated on a long session name, and a truncated "not yet released" is
                    // worse than no tag at all — it reads as a corrupted line, not a status.
                    String releaseTag = submission.getFeedbackStatus() == FeedbackStatus.PUBLISHED
                            ? "released" : "DRAFT - NOT YET RELEASED";
                    indexEntries.add(new IndexEntry(learner, SECTION_FOLDERS.get(5),
                            sessionName + " feedback v" + ordinal, submission.getGradedAt(), releaseTag));

                    if (submission.getMarkedFilePath() != null && !submission.getMarkedFilePath().isBlank()) {
                        String markedEntry = sectionPath(moduleBase, 5) + canonicalMarkedCopyFilename(learner, sessionName, ordinal);
                        writeStoredFile(zos, markedEntry, submission.getMarkedFilePath(), "marked copy", counters);
                        indexEntries.add(new IndexEntry(learner, SECTION_FOLDERS.get(5),
                                sessionName + " marked copy v" + ordinal, submission.getGradedAt()));
                    }
                }
            }
        }
    }

    /**
     * Everything belonging to one learner, resolved exactly as the zip resolves it, with no I/O
     * or zip-writing side effects.
     *
     * <p>This is the one place "what belongs where" is decided — which guide version is pinned,
     * which submission belongs to which module, what a session's Nth resubmission is called.
     * {@link #writeLearnerFolder} and {@code PoePortfolioBrowserService} both call this and
     * nothing else to answer that question, so the on-screen portfolio browser and the zip an
     * admin downloads can never disagree about what is in a learner's portfolio — they are
     * reading the same resolution, not two queries that happen to agree today.
     *
     * <p>{@code sectionOnlyCategoryId} narrows modules to one category, exactly as a SECTION-scope
     * export does; personal documents (sections 1/2/6 — they do not belong to a category) are
     * omitted in that case, matching {@code writeLearnerFolder}'s previous behaviour.
     */
    @Transactional(readOnly = true)
    LearnerPortfolio resolvePortfolio(Learner learner, Long sectionOnlyCategoryId) {
        List<LearnerDocument> personalDocuments = sectionOnlyCategoryId == null
                ? documentRepository.findByLearner_IdOrderByDocumentTypeAscVersionAsc(learner.getId())
                : List.of();

        List<Module> modules = moduleRepository.findByLearnerIdIn(List.of(learner.getId()));
        if (sectionOnlyCategoryId != null) {
            modules = modules.stream()
                    .filter(m -> m.getCategory() != null && sectionOnlyCategoryId.equals(m.getCategory().getId()))
                    .toList();
        }
        List<Submission> submissions = submissionRepository.findByLearner_IdOrderBySubmittedAtAsc(learner.getId());

        List<ModulePortfolioFolder> moduleFolders = new ArrayList<>();
        for (Module module : modules) {
            List<Submission> onThisModule = submissions.stream()
                    .filter(s -> onModule(s, module))
                    .sorted(Comparator.comparing(Submission::getSubmittedAt))
                    .toList();

            ModuleFile guide = resolveGuide(module, onThisModule);

            Map<Long, Integer> ordinalBySession = new HashMap<>();
            List<SubmissionOnModule> submissionsWithOrdinals = new ArrayList<>();
            for (Submission submission : onThisModule) {
                Long sessionId = submission.getSession().getId();
                int ordinal = ordinalBySession.merge(sessionId, 1, Integer::sum);
                submissionsWithOrdinals.add(new SubmissionOnModule(
                        submission, submission.getSession().getSessionName(), ordinal));
            }

            moduleFolders.add(new ModulePortfolioFolder(module, categoryFolderFor(module), guide, submissionsWithOrdinals));
        }

        return new LearnerPortfolio(learner, personalDocuments, moduleFolders);
    }

    /** One learner's fully resolved portfolio — see {@link #resolvePortfolio}. */
    record LearnerPortfolio(Learner learner, List<LearnerDocument> personalDocuments,
                             List<ModulePortfolioFolder> moduleFolders) { }

    /** One module's section 3/4/5 content for one learner: its pinned guide and its submissions. */
    record ModulePortfolioFolder(Module module, String categoryFolder, ModuleFile guide,
                                  List<SubmissionOnModule> submissions) { }

    /** A submission together with the session name and per-session ordinal it renders under. */
    record SubmissionOnModule(Submission submission, String sessionName, int ordinal) { }

    boolean onModule(Submission submission, Module module) {
        return submission.getSession() != null
                && submission.getSession().getAssignment() != null
                && submission.getSession().getAssignment().getModule() != null
                && module.getId().equals(submission.getSession().getAssignment().getModule().getId());
    }

    private String sectionPath(String moduleBaseTemplate, int section) {
        return String.format(moduleBaseTemplate, SECTION_FOLDERS.get(section));
    }

    /**
     * The version of the guide this learner actually worked from — a pinned {@code
     * guideVersionId} on one of their submissions to this module, where one is set, otherwise
     * whatever is current.
     *
     * <p>Nothing writes {@code guideVersionId} yet (it is a Phase 2 column with no writer
     * until a later phase pins it), so this always falls through to the current version today
     * — correctly, not as a bug. The resolution exists now so a moderator sees the brief a
     * learner actually worked from the moment something starts recording it, instead of this
     * export needing a second change later.
     */
    ModuleFile resolveGuide(Module module, List<Submission> submissionsOnThisModule) {
        List<ModuleFile> files = moduleFileRepository.findByModuleId(module.getId());
        Long pinnedId = submissionsOnThisModule.stream()
                .map(Submission::getGuideVersionId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (pinnedId != null) {
            for (ModuleFile file : files) {
                if (pinnedId.equals(file.getId())) {
                    return file;
                }
            }
        }
        return files.stream()
                .filter(f -> Integer.valueOf(3).equals(f.getPoeSection()) && Boolean.TRUE.equals(f.getCurrent()))
                .findFirst()
                .orElse(null);
    }

    /**
     * The marking record as plain text: outcome, marks, who marked it and when, the written
     * feedback, and whether the learner has actually seen it. Rendered rather than the
     * annotated PDF itself — the in-app pen annotations (Phase 5) are replayed client-side over
     * the original file and there is no server-side renderer for them yet, so this is what can
     * be handed over honestly today. It deliberately ignores {@code isMarkingVisibleToLearner}:
     * this is the one place an INTERNAL moderator report is meant to appear.
     *
     * <p>Unreleased (DRAFT) marking is included, not filtered out. A moderator reviewing a
     * portfolio needs to see the assessor's judgement whether or not the learner has been told
     * yet — Phase 5 made release a learner-visibility gate, not an existence gate — but nobody
     * reading this file may mistake a draft for a final result, so a draft says so twice: a
     * banner at the very top of the text, and the release state on this entry's line in
     * {@code 00_INDEX.pdf}.
     */
    String renderFeedback(Submission submission) {
        StringBuilder sb = new StringBuilder();
        if (submission.getFeedbackStatus() != FeedbackStatus.PUBLISHED) {
            sb.append("*** DRAFT — this marking has not been released to the learner. ")
                    .append("Treat it as preliminary. ***\n\n");
        }
        sb.append("Outcome: ").append(submission.getStatus() == null ? "Not recorded" : submission.getStatus()).append('\n');
        if (submission.getMarksAwarded() != null) {
            sb.append("Marks: ").append(submission.getMarksAwarded()).append('\n');
        }
        sb.append("Graded by: ").append(submission.getGradedByName() == null ? "Not recorded" : submission.getGradedByName())
                .append(" (").append(submission.getGradedByRole() == null ? "role not recorded" : submission.getGradedByRole()).append(")\n");
        sb.append("Graded at: ").append(submission.getGradedAt()).append('\n');
        sb.append("Visibility: ").append(submission.getFeedbackVisibility() == FeedbackVisibility.INTERNAL
                ? "INTERNAL — an assessor/moderator record, not released to the learner"
                : "Learner-facing").append('\n');
        sb.append("Released to learner: ").append(submission.isMarkingVisibleToLearner() ? "Yes" : "No").append('\n');
        sb.append("\nFeedback:\n");
        sb.append(submission.getFeedback() == null || submission.getFeedback().isBlank()
                ? "(no written feedback recorded)" : submission.getFeedback());
        sb.append('\n');
        if (submission.getAnnotationsJson() != null && !submission.getAnnotationsJson().isBlank()) {
            sb.append("\nNote: this submission also carries in-app pen annotations, viewable in the portal's ")
                    .append("marking screen. They are not rendered into this file.\n");
        }
        return sb.toString();
    }

    private void writeStoredFile(ZipOutputStream zos, String entryName, String filePath, String description, Counters counters) {
        if (filePath == null || filePath.isBlank()) {
            counters.filesSkipped++;
            return;
        }
        try {
            byte[] bytes = storedFileService.readBytes(filePath, description);
            writeBytes(zos, entryName, bytes, counters);
        } catch (Exception e) {
            log.warn("Skipping a {} in export: could not read the stored file ({}).", description, e.getMessage());
            counters.filesSkipped++;
        }
    }

    private void writeBytes(ZipOutputStream zos, String entryName, byte[] bytes, Counters counters) throws IOException {
        writeRawEntry(zos, entryName, bytes);
        counters.filesWritten++;
    }

    private void writeRawEntry(ZipOutputStream zos, String entryName, byte[] bytes) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(bytes);
        zos.closeEntry();
    }

    // ================================================================== 00_INDEX.pdf

    private void writeIndexPdf(ZipOutputStream zos, ExportJob job, List<Learner> learners,
                                List<IndexEntry> entries, Counters counters) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float margin = 50;
            float fontSize = 9;

            PDPage[] page = {new PDPage(PDRectangle.A4)};
            doc.addPage(page[0]);
            PDPageContentStream[] cs = {new PDPageContentStream(doc, page[0])};
            float[] y = {page[0].getMediaBox().getHeight() - margin};

            y[0] = writeLine(cs[0], bold, 14, margin, y[0], "SETA Portfolio Export - " + job.getScopeLabel());
            y[0] -= 6;
            y[0] = writeLine(cs[0], font, fontSize, margin, y[0], "Generated: " + LocalDateTime.now());
            y[0] = writeLine(cs[0], font, fontSize, margin, y[0], "Learners in this export: " + learners.size());
            y[0] = writeLine(cs[0], font, fontSize, margin, y[0], "Files included: " + counters.filesWritten
                    + (counters.filesSkipped > 0
                            ? " (" + counters.filesSkipped + " could not be read and were skipped - see the server log)"
                            : ""));
            y[0] -= 6;
            y[0] = writeWrapped(cs[0], font, fontSize, margin, y[0], page[0].getMediaBox().getWidth() - 2 * margin,
                    "Signature status: not available. Digital signature capture is not yet built (Phase 9 of the "
                            + "implementation plan); every row below reads \"Not signed\" until that ships. "
                            + "SIGNATURES.csv in this bundle carries the header SETA expects and no rows, for the "
                            + "same reason.");
            y[0] -= 10;

            if (learners.isEmpty()) {
                y[0] = writeLine(cs[0], font, fontSize, margin, y[0], "No learners are in scope for this export.");
            }

            Map<Long, List<IndexEntry>> byLearner = entries.stream()
                    .collect(Collectors.groupingBy(e -> e.learner().getId(), LinkedHashMap::new, Collectors.toList()));

            for (Learner learner : learners) {
                if (y[0] < margin + 60) {
                    cs[0].close();
                    page[0] = new PDPage(PDRectangle.A4);
                    doc.addPage(page[0]);
                    cs[0] = new PDPageContentStream(doc, page[0]);
                    y[0] = page[0].getMediaBox().getHeight() - margin;
                }
                y[0] = writeLine(cs[0], bold, 11, margin, y[0], learner.getFullName() + " (" + learner.getLearnerCode() + ")");
                List<IndexEntry> mine = byLearner.getOrDefault(learner.getId(), List.of());
                if (mine.isEmpty()) {
                    y[0] = writeLine(cs[0], font, fontSize, margin + 12, y[0], "No files on record for this learner.");
                }
                for (IndexEntry entry : mine) {
                    if (y[0] < margin + 20) {
                        cs[0].close();
                        page[0] = new PDPage(PDRectangle.A4);
                        doc.addPage(page[0]);
                        cs[0] = new PDPageContentStream(doc, page[0]);
                        y[0] = page[0].getMediaBox().getHeight() - margin;
                    }
                    // The tag (draft/released) is never truncated, even if the label is: a
                    // clipped free-text label just looks abbreviated, but a clipped status tag
                    // reads as a different, wrong status, or as a corrupted line. The label's
                    // truncation budget shrinks to make room for it instead.
                    String tagSuffix = entry.tag() == null ? "" : " [" + entry.tag() + "]";
                    int labelBudget = Math.max(15, 50 - tagSuffix.length());
                    String line = String.format("[%s] %s%s - %s - Not signed",
                            entry.section(), truncate(entry.label(), labelBudget), tagSuffix,
                            entry.at() == null ? "date not recorded" : entry.at().toString());
                    y[0] = writeLine(cs[0], font, fontSize, margin + 12, y[0], line);
                }
                y[0] -= 8;
            }
            cs[0].close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            writeRawEntry(zos, "00_INDEX.pdf", baos.toByteArray());
        }
    }

    private float writeLine(PDPageContentStream cs, PDFont font, float fontSize, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitizeForPdf(text));
        cs.endText();
        return y - (fontSize + 5);
    }

    private float writeWrapped(PDPageContentStream cs, PDFont font, float fontSize, float x, float y, float maxWidth, String text) throws IOException {
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        float current = y;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float width = font.getStringWidth(sanitizeForPdf(candidate)) / 1000 * fontSize;
            if (width > maxWidth && !line.isEmpty()) {
                current = writeLine(cs, font, fontSize, x, current, line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            current = writeLine(cs, font, fontSize, x, current, line.toString());
        }
        return current;
    }

    /** Base-14 PDF fonts only cover WinAnsi; anything outside it becomes '?' rather than a
     *  thrown exception that would fail an entire learnership's export over one character. */
    private String sanitizeForPdf(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 32 && c <= 255 ? c : '?');
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ================================================================== SIGNATURES.csv

    /**
     * The manifest SETA expects, with no rows in it.
     *
     * {@code signature_events} is Phase 9 — not built yet. Omitting this file entirely would
     * leave a bundle that does not match the format an auditor is told to expect; a header row
     * and an explanatory comment is the honest middle ground between fabricating signature data
     * and silently dropping the file.
     */
    private void writeSignaturesCsv(ZipOutputStream zos) throws IOException {
        String content = "# Digital signature capture is not yet built (Phase 9 of the implementation plan).\n"
                + "# This file carries the header format SETA expects and no data rows.\n"
                + "verification_code,signable_type,signable_id,signer_role,signer_id,signed_at,revoked_at\n";
        writeRawEntry(zos, "SIGNATURES.csv", content.getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================== canonical filenames

    String canonicalDocumentFilename(Learner learner, LearnerDocument doc) {
        String docToken = doc.getDocumentType().getLabel().replace(" ", "-");
        return String.format("%s_%s_%s_%s_v%d%s",
                learner.getLearnerCode(), surname(learner.getFullName()), initials(learner.getFullName()),
                docToken, doc.getVersion(), extensionOf(doc.getOriginalFilename()));
    }

    String canonicalGuideFilename(Module module, ModuleFile file) {
        String code = module.getModuleCode() == null ? "MODULE" : module.getModuleCode();
        return String.format("%s_%s_v%d%s", sanitizeSegment(code), sanitizeSegment(file.getTitle()),
                file.getVersion(), extensionOf(file.getOriginalFilename()));
    }

    String canonicalSubmissionFilename(Learner learner, String sessionName, int ordinal, Submission submission) {
        return String.format("%s_%s_%s_%s_v%d%s",
                learner.getLearnerCode(), surname(learner.getFullName()), initials(learner.getFullName()),
                sanitizeSegment(sessionName), ordinal, extensionOf(submission.getOriginalFilename()));
    }

    String canonicalFeedbackFilename(Learner learner, String sessionName, int ordinal) {
        return String.format("%s_%s_%s_%s_Feedback_v%d.txt",
                learner.getLearnerCode(), surname(learner.getFullName()), initials(learner.getFullName()),
                sanitizeSegment(sessionName), ordinal);
    }

    String canonicalMarkedCopyFilename(Learner learner, String sessionName, int ordinal) {
        // markedFilePath is a storage reference (a Cloudinary public_id or a disk path), not a
        // real filename, and this codebase's public_ids are deliberately extension-less — so
        // unlike the other canonical names, there is no real extension to preserve here.
        return String.format("%s_%s_%s_%s_Marked_v%d.pdf",
                learner.getLearnerCode(), surname(learner.getFullName()), initials(learner.getFullName()),
                sanitizeSegment(sessionName), ordinal);
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[0];
        }
        return fullName.trim().split("\\s+");
    }

    private String surname(String fullName) {
        String[] parts = splitName(fullName);
        return parts.length == 0 ? "Learner" : parts[parts.length - 1];
    }

    private String initials(String fullName) {
        String[] parts = splitName(fullName);
        if (parts.length <= 1) {
            return "X";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.isEmpty() ? "X" : sb.toString();
    }

    String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot) : "";
    }

    String sanitizeSegment(String s) {
        if (s == null || s.isBlank()) {
            return "Unnamed";
        }
        String cleaned = s.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
        return cleaned.isEmpty() ? "Unnamed" : cleaned;
    }

    String categoryFolder(String categoryType) {
        if (categoryType == null) {
            return "Other";
        }
        return CATEGORY_FOLDERS.getOrDefault(categoryType.toUpperCase(Locale.ROOT), categoryType);
    }

    String categoryFolderFor(Module module) {
        if (module.getCategory() == null || module.getCategory().getCategoryType() == null) {
            return "Uncategorised";
        }
        return categoryFolder(module.getCategory().getCategoryType());
    }

    // ================================================================== scope resolution

    private List<Long> resolveLearnerIds(ExportJob job) {
        Map<String, Object> ref = readJson(job.getScopeRef());
        return switch (job.getScopeType()) {
            case LEARNER -> List.of(asLong(ref.get("learnerId")));
            case COHORT -> learnerRepository.findByLearnership_IdAndCohort(
                            asLong(ref.get("learnershipId")), (String) ref.get("cohort"))
                    .stream().map(Learner::getId).toList();
            case LEARNERSHIP, SECTION -> learnerRepository.findByLearnership_Id(asLong(ref.get("learnershipId")))
                    .stream().map(Learner::getId).toList();
            case MODERATION_SAMPLE -> {
                StaffPrincipal target = new StaffPrincipal(
                        StaffRole.valueOf((String) ref.get("staffRole")), asLong(ref.get("staffId")), null);
                yield new ArrayList<>(scopeService.accessibleLearnerIds(target));
            }
        };
    }

    private Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    // ================================================================== small helpers

    private void requireAdmin(StaffPrincipal principal, String scopeName) {
        if (!principal.isAdmin()) {
            throw new AccessDeniedException("Only an admin may request a " + scopeName + " export.");
        }
    }

    private Learnership requireLearnership(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("learnershipId is required for this export scope.");
        }
        return learnershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Learnership not found"));
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private ExportScopeType parseScopeType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("scopeType is required.");
        }
        try {
            return ExportScopeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown export scope: " + raw);
        }
    }

    private StaffPrincipal resolveStaff(String staffRoleRaw, Long staffId) {
        StaffRole role;
        try {
            role = StaffRole.valueOf(staffRoleRaw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("staffRole must be ASSESSOR or MODERATOR.");
        }
        return switch (role) {
            case ASSESSOR -> {
                Assessor a = assessorRepository.findById(staffId)
                        .orElseThrow(() -> new ResourceNotFoundException("Assessor not found"));
                yield StaffPrincipal.assessor(a.getId(), a.getUsername());
            }
            case MODERATOR -> {
                Moderator m = moderatorRepository.findById(staffId)
                        .orElseThrow(() -> new ResourceNotFoundException("Moderator not found"));
                yield StaffPrincipal.moderator(m.getId(), m.getUsername());
            }
            default -> throw new IllegalArgumentException("staffRole must be ASSESSOR or MODERATOR.");
        };
    }

    private String staffDisplayName(StaffPrincipal target) {
        return switch (target.role()) {
            case ASSESSOR -> assessorRepository.findById(target.id()).map(Assessor::getFullName).orElse("Assessor #" + target.id());
            case MODERATOR -> moderatorRepository.findById(target.id()).map(Moderator::getFullName).orElse("Moderator #" + target.id());
            default -> "Staff #" + target.id();
        };
    }

    private String requesterUsername(ExportJob job) {
        try {
            return switch (StaffRole.valueOf(job.getRequestedByRole())) {
                case ADMIN -> adminRepository.findById(job.getRequestedById()).map(Admin::getUsername).orElse("unknown-admin");
                case ASSESSOR -> assessorRepository.findById(job.getRequestedById()).map(Assessor::getUsername).orElse("unknown-assessor");
                case MODERATOR -> moderatorRepository.findById(job.getRequestedById()).map(Moderator::getUsername).orElse("unknown-moderator");
                case LECTURER -> "unknown-lecturer";
            };
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Tells the requester their export is ready, when they have an email on file. Admin has no
     * email column in this system at all, so an admin-requested export is status-only —
     * polled from the Exports panel, never emailed.
     */
    private void notifyRequester(ExportJob job) {
        try {
            StaffRole role = StaffRole.valueOf(job.getRequestedByRole());
            String email = null;
            String name = null;
            if (role == StaffRole.ASSESSOR) {
                Assessor a = assessorRepository.findById(job.getRequestedById()).orElse(null);
                if (a != null) {
                    email = a.getEmail();
                    name = a.getFullName();
                }
            } else if (role == StaffRole.MODERATOR) {
                Moderator m = moderatorRepository.findById(job.getRequestedById()).orElse(null);
                if (m != null) {
                    email = m.getEmail();
                    name = m.getFullName();
                }
            }
            if (email != null && !email.isBlank()) {
                emailService.sendExportReadyEmail(email, name == null ? "there" : name, job.getScopeLabel());
            }
        } catch (Exception e) {
            log.warn("Could not send export-ready notification for job {}: {}", job.getId(), e.getMessage());
        }
    }

    /**
     * Says which stage failed <em>and</em> why, where "why" can be said safely.
     *
     * <p>Used to report only the stage — "the export failed while storing the finished export"
     * — which is exactly what left a real production failure undiagnosable: the zip built fine,
     * storing it failed, and the column an admin could actually see never said whether that was
     * a transient network blip worth retrying or a file over some provider's plan-imposed limit
     * that would fail identically every time. The underlying exception's own message is what
     * tells those two apart, so it is included here — sanitized first, on the same discipline as
     * {@link NotificationService#stripLinks}: an error message is not a place a signed URL
     * should ever be able to surface, however unlikely that is from a plain upload or disk
     * failure, and the result is still rendered straight back to an admin dashboard. Capped to
     * fit the column with room for the stage prefix.
     */
    private String safeErrorMessage(String stage, Exception e) {
        StringBuilder message = new StringBuilder("The export failed while ").append(stage);
        String detail = sanitizedDetail(e.getMessage());
        if (detail != null) {
            message.append(": ").append(detail);
        }
        if (e instanceof IOException) {
            message.append(" (this can mean the instance is low on disk space)");
        }
        message.append(". See the server log for full detail.");
        return message.length() > 500 ? message.substring(0, 497) + "..." : message.toString();
    }

    /** Strips anything link-shaped and caps length; null in, null out. */
    private String sanitizedDetail(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }
        String cleaned = rawMessage.replaceAll("(?i)\\b(?:https?://|www\\.)\\S+", "[link removed]").trim();
        return cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned;
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encode export scope.", e);
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Could not decode export scope for job.", e);
        }
    }

    private static class Counters {
        int filesWritten = 0;
        int filesSkipped = 0;
    }

    private record IndexEntry(Learner learner, String section, String label, LocalDateTime at, String tag) {
        IndexEntry(Learner learner, String section, String label, LocalDateTime at) {
            this(learner, section, label, at, null);
        }
    }
}
