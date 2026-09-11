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
    private final CloudinaryService cloudinaryService;
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

    private static final Map<Integer, String> SECTION_FOLDERS = Map.of(
            1, "1. PERSONAL DETAILS",
            2, "2. QUALIFICATION DETAILS",
            3, "3. ASSESSMENT GUIDELINES",
            4, "4. ASSESSMENT ACTIVITIES",
            5, "5. FEEDBACK",
            6, "6. ADDITIONAL EVIDENCE"
    );

    private static final Map<String, String> CATEGORY_FOLDERS = Map.of(
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
        job.setStatus(ExportJobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        exportJobRepository.save(job);

        Path tempZip = null;
        try {
            List<Learner> learners = resolveLearnerIds(job).stream()
                    .map(id -> learnerRepository.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Learner::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();

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

            String resultId = store(tempZip, jobId);
            // The local-disk fallback moves the temp file to its resting place, so there is
            // nothing left at tempZip to clean up. The Cloudinary path uploads *from* the file
            // without consuming it, so tempZip still exists on disk afterwards and must stay
            // non-null here — the finally block below is what deletes it. Getting this branch
            // backwards would leak one temp file per successful export, on the one instance
            // where a slow disk leak matters most.
            if (!cloudinaryService.isConfigured()) {
                tempZip = null;
            }

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
            log.error("PoE export {} failed.", jobId, e);
            job.setStatus(ExportJobStatus.FAILED);
            job.setError(safeErrorMessage(e));
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
     * Uploads the finished zip as an authenticated raw resource when Cloudinary is configured,
     * or moves it into the same private, non-statically-served directory the learner document
     * vault falls back to otherwise. Either way the result is read back through
     * {@link StoredFileService} exactly like any other stored file — the download endpoint
     * needs no export-specific storage logic.
     */
    private String store(Path tempZip, Long jobId) throws IOException {
        String filename = "poe_export_" + jobId + ".zip";
        if (cloudinaryService.isConfigured()) {
            return cloudinaryService.uploadLearnerFile(tempZip.toFile(), filename);
        }
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

        if (sectionOnlyCategoryId == null) {
            for (LearnerDocument doc : documentRepository.findByLearner_IdOrderByDocumentTypeAscVersionAsc(learner.getId())) {
                if (doc.getDocumentType() == null) {
                    continue;
                }
                String sectionFolder = SECTION_FOLDERS.getOrDefault(doc.getDocumentType().getPoeSection(), SECTION_FOLDERS.get(6));
                String entryName = folder + sectionFolder + "/" + canonicalDocumentFilename(learner, doc);
                writeStoredFile(zos, entryName, doc.getFilePath(), "document", counters);
                indexEntries.add(new IndexEntry(learner, sectionFolder,
                        doc.getDocumentType().getLabel() + " v" + doc.getVersion(), doc.getUploadedAt()));
            }
        }

        List<Module> modules = moduleRepository.findByLearnerIdIn(List.of(learner.getId()));
        if (sectionOnlyCategoryId != null) {
            modules = modules.stream()
                    .filter(m -> m.getCategory() != null && sectionOnlyCategoryId.equals(m.getCategory().getId()))
                    .toList();
        }
        List<Submission> submissions = submissionRepository.findByLearner_IdOrderBySubmittedAtAsc(learner.getId());

        for (Module module : modules) {
            String categoryFolder = categoryFolderFor(module);
            String moduleBase = folder + "%s/" + categoryFolder + "/" + sanitizeSegment(module.getModuleName()) + "/";

            List<Submission> onThisModule = submissions.stream()
                    .filter(s -> onModule(s, module))
                    .sorted(Comparator.comparing(Submission::getSubmittedAt))
                    .toList();

            ModuleFile guide = resolveGuide(module, onThisModule);
            if (guide != null) {
                String entryName = sectionPath(moduleBase, 3) + canonicalGuideFilename(module, guide);
                writeStoredFile(zos, entryName, guide.getFilePath(), "facilitator guide", counters);
                indexEntries.add(new IndexEntry(learner, SECTION_FOLDERS.get(3),
                        module.getModuleName() + " guide v" + guide.getVersion(), guide.getCreatedAt()));
            }

            Map<Long, Integer> ordinalBySession = new HashMap<>();
            for (Submission submission : onThisModule) {
                Long sessionId = submission.getSession().getId();
                int ordinal = ordinalBySession.merge(sessionId, 1, Integer::sum);
                String sessionName = submission.getSession().getSessionName();

                String activityEntry = sectionPath(moduleBase, 4) + canonicalSubmissionFilename(learner, sessionName, ordinal, submission);
                writeStoredFile(zos, activityEntry, submission.getFilePath(), "submission", counters);
                indexEntries.add(new IndexEntry(learner, SECTION_FOLDERS.get(4),
                        sessionName + " v" + ordinal, submission.getSubmittedAt()));

                if (submission.getGradedAt() != null) {
                    String feedbackEntry = sectionPath(moduleBase, 5) + canonicalFeedbackFilename(learner, sessionName, ordinal);
                    writeBytes(zos, feedbackEntry, renderFeedback(submission).getBytes(StandardCharsets.UTF_8), counters);
                    indexEntries.add(new IndexEntry(learner, SECTION_FOLDERS.get(5),
                            sessionName + " feedback v" + ordinal, submission.getGradedAt()));

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

    private boolean onModule(Submission submission, Module module) {
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
    private ModuleFile resolveGuide(Module module, List<Submission> submissionsOnThisModule) {
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
     */
    private String renderFeedback(Submission submission) {
        StringBuilder sb = new StringBuilder();
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
                    String line = String.format("[%s] %s - %s - Not signed",
                            entry.section(), truncate(entry.label(), 50),
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

    private String canonicalDocumentFilename(Learner learner, LearnerDocument doc) {
        String docToken = doc.getDocumentType().getLabel().replace(" ", "-");
        return String.format("%s_%s_%s_%s_v%d%s",
                learner.getLearnerCode(), surname(learner.getFullName()), initials(learner.getFullName()),
                docToken, doc.getVersion(), extensionOf(doc.getOriginalFilename()));
    }

    private String canonicalGuideFilename(Module module, ModuleFile file) {
        String code = module.getModuleCode() == null ? "MODULE" : module.getModuleCode();
        return String.format("%s_%s_v%d%s", sanitizeSegment(code), sanitizeSegment(file.getTitle()),
                file.getVersion(), extensionOf(file.getOriginalFilename()));
    }

    private String canonicalSubmissionFilename(Learner learner, String sessionName, int ordinal, Submission submission) {
        return String.format("%s_%s_%s_%s_v%d%s",
                learner.getLearnerCode(), surname(learner.getFullName()), initials(learner.getFullName()),
                sanitizeSegment(sessionName), ordinal, extensionOf(submission.getOriginalFilename()));
    }

    private String canonicalFeedbackFilename(Learner learner, String sessionName, int ordinal) {
        return String.format("%s_%s_%s_%s_Feedback_v%d.txt",
                learner.getLearnerCode(), surname(learner.getFullName()), initials(learner.getFullName()),
                sanitizeSegment(sessionName), ordinal);
    }

    private String canonicalMarkedCopyFilename(Learner learner, String sessionName, int ordinal) {
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

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot) : "";
    }

    private String sanitizeSegment(String s) {
        if (s == null || s.isBlank()) {
            return "Unnamed";
        }
        String cleaned = s.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
        return cleaned.isEmpty() ? "Unnamed" : cleaned;
    }

    private String categoryFolder(String categoryType) {
        if (categoryType == null) {
            return "Other";
        }
        return CATEGORY_FOLDERS.getOrDefault(categoryType.toUpperCase(Locale.ROOT), categoryType);
    }

    private String categoryFolderFor(Module module) {
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
     * Never built from a raw exception message — the same discipline as everywhere else a
     * signed URL could conceivably surface in an underlying I/O error, and this column is
     * rendered straight back to an admin dashboard.
     */
    private String safeErrorMessage(Exception e) {
        if (e instanceof IllegalStateException && e.getMessage() != null
                && e.getMessage().contains("Cloudinary is not configured")) {
            return "Storage is not configured on this deployment.";
        }
        return "The export could not be completed. See the server log for detail.";
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

    private record IndexEntry(Learner learner, String section, String label, LocalDateTime at) { }
}
