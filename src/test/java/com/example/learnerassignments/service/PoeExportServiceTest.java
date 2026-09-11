package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.PoeExportDtos.CreateExportRequest;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.security.StaffPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The SETA export, tested against what it actually promises: the right people are in scope,
 * the right people are excluded, one bad file does not sink the job, and every version of every
 * document survives — including the one kind of record this export exists specifically to
 * surface that the learner portal never shows: an INTERNAL moderator report.
 *
 * Runs each build through {@link PoeExportService#runExport}, the synchronous half of the
 * worker, in the same transaction as its fixture — the {@code @Async} entry point is exercised
 * by the controller in production, not here, for the reason recorded on that method.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PoeExportServiceTest {

    @Autowired PoeExportService exportService;
    @Autowired ExportJobRepository exportJobRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired ModuleFileRepository moduleFileRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired LearnerDocumentRepository documentRepository;
    @Autowired AssessorRepository assessorRepository;
    @Autowired ModeratorRepository moderatorRepository;
    @Autowired AssessorAssignmentRepository assessorAssignmentRepository;
    @Autowired ModeratorAssignmentRepository moderatorAssignmentRepository;

    private static final StaffPrincipal ADMIN = StaffPrincipal.admin(1L, "admin");

    // ------------------------------------------------------------------ scope validation

    @Test
    @DisplayName("An admin can request a whole-learnership export")
    void adminCanExportLearnership() {
        Learnership learnership = learnership("Admin");
        learner(learnership, "A", LocalDateTime.now());
        learner(learnership, "A", LocalDateTime.now());

        ExportJob job = exportService.createJob(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        assertThat(job.getLearnerCount()).isEqualTo(2);
        assertThat(job.getScopeLabel()).isEqualTo(learnership.getName());
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.QUEUED);
    }

    @Test
    @DisplayName("A non-admin cannot request a learnership export")
    void nonAdminCannotExportLearnership() {
        Learnership learnership = learnership("Guarded");
        Assessor assessor = assessor();

        assertThatThrownBy(() -> exportService.createJob(
                StaffPrincipal.assessor(assessor.getId(), assessor.getUsername()),
                request("LEARNERSHIP", learnership.getId(), null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("A cohort export includes only that cohort within that learnership")
    void cohortScopeFiltersCorrectly() {
        Learnership learnership = learnership("Cohorts");
        Learner january = learner(learnership, "JAN-2026", LocalDateTime.now());
        learner(learnership, "JUL-2026", LocalDateTime.now());

        CreateExportRequest req = request("COHORT", learnership.getId(), null, null);
        req.setCohort("JAN-2026");
        ExportJob job = exportService.createJob(ADMIN, req);

        assertThat(job.getLearnerCount()).isEqualTo(1);
        assertThat(january.getId()).isNotNull();
    }

    @Test
    @DisplayName("A section export rejects a category that belongs to a different learnership")
    void sectionRejectsMismatchedCategory() {
        Learnership mine = learnership("Mine");
        Learnership theirs = learnership("Theirs");
        Category otherCategory = category(theirs, "CORE");

        CreateExportRequest req = request("SECTION", mine.getId(), null, otherCategory.getId());

        assertThatThrownBy(() -> exportService.createJob(ADMIN, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("An admin can request one specific learner")
    void adminCanExportOneLearner() {
        Learnership learnership = learnership("Solo");
        Learner learner = learner(learnership, "A", LocalDateTime.now());

        ExportJob job = exportService.createJob(ADMIN, request("LEARNER", null, learner.getId(), null));

        assertThat(job.getLearnerCount()).isEqualTo(1);
        assertThat(job.getScopeLabel()).contains(learner.getLearnerCode());
    }

    @Test
    @DisplayName("An assessor cannot export a learner they are not assigned to")
    void assessorCannotExportUnassignedLearner() {
        Learnership learnership = learnership("Fenced");
        Learner learner = learner(learnership, "A", LocalDateTime.now());
        Assessor assessor = assessor();

        assertThatThrownBy(() -> exportService.createJob(
                StaffPrincipal.assessor(assessor.getId(), assessor.getUsername()),
                request("LEARNER", null, learner.getId(), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("An assessor can export a learner they are assigned to")
    void assessorCanExportAssignedLearner() {
        Learnership learnership = learnership("Assigned");
        Learner learner = learner(learnership, "A", LocalDateTime.now());
        Assessor assessor = assessor();
        assessorAssignmentRepository.save(AssessorAssignment.builder()
                .assessor(assessor).learner(learner).assignedAt(LocalDateTime.now()).build());

        ExportJob job = exportService.createJob(
                StaffPrincipal.assessor(assessor.getId(), assessor.getUsername()),
                request("LEARNER", null, learner.getId(), null));

        assertThat(job.getLearnerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A moderation-sample export always resolves to the caller's own reach, never a named id")
    void moderationSampleIsAlwaysSelfForNonAdmins() {
        Learnership learnership = learnership("Sample");
        Learner mine = learner(learnership, "A", LocalDateTime.now());
        Moderator me = moderator();
        Moderator someoneElse = moderator();
        moderatorAssignmentRepository.save(ModeratorAssignment.builder()
                .moderator(me).learnership(learnership).scope(ModerationScope.SAMPLE)
                .assignedAt(LocalDateTime.now()).build());

        // Even if the request names someone else's id, a non-admin caller cannot pull it —
        // createJob ignores staffRole/staffId entirely for a non-admin caller.
        CreateExportRequest req = request("MODERATION_SAMPLE", null, null, null);
        req.setStaffRole("MODERATOR");
        req.setStaffId(someoneElse.getId());

        ExportJob job = exportService.createJob(
                StaffPrincipal.moderator(me.getId(), me.getUsername()), req);

        assertThat(job.getLearnerCount()).isEqualTo(1);
        assertThat(mine.getId()).isNotNull();
    }

    @Test
    @DisplayName("An admin requesting a moderation sample must name a staff member")
    void adminModerationSampleRequiresStaffFields() {
        assertThatThrownBy(() -> exportService.createJob(ADMIN, request("MODERATION_SAMPLE", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("An admin can request a moderation sample on a named moderator's behalf")
    void adminCanRequestModerationSampleForNamedModerator() {
        Learnership learnership = learnership("OnBehalf");
        learner(learnership, "A", LocalDateTime.now());
        Moderator moderator = moderator();
        moderatorAssignmentRepository.save(ModeratorAssignment.builder()
                .moderator(moderator).learnership(learnership).scope(ModerationScope.SAMPLE)
                .assignedAt(LocalDateTime.now()).build());

        CreateExportRequest req = request("MODERATION_SAMPLE", null, null, null);
        req.setStaffRole("MODERATOR");
        req.setStaffId(moderator.getId());
        ExportJob job = exportService.createJob(ADMIN, req);

        assertThat(job.getLearnerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("An unknown scope type is rejected before anything is created")
    void unknownScopeTypeRejected() {
        assertThatThrownBy(() -> exportService.createJob(ADMIN, request("NONSENSE", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ visibility of a job

    @Test
    @DisplayName("An admin can see any job; a non-owning assessor cannot — and gets 404, not 403")
    void jobVisibilityIsOwnerOrAdmin() {
        Learnership learnership = learnership("Visibility");
        learner(learnership, "A", LocalDateTime.now());
        ExportJob job = exportService.createJob(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        assertThat(exportService.requireVisible(job.getId(), ADMIN)).isEqualTo(job);

        Assessor stranger = assessor();
        assertThatThrownBy(() -> exportService.requireVisible(job.getId(),
                StaffPrincipal.assessor(stranger.getId(), stranger.getUsername())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ the built zip

    @Test
    @DisplayName("The zip contains a document, a guide, a submission, feedback, the index and the signatures manifest")
    void zipContainsEveryPoeSection() throws Exception {
        Learnership learnership = learnership("FullBuild");
        Learner learner = learner(learnership, "A", LocalDateTime.now().minusDays(10));
        Module module = module(learnership, "CORE");
        enrol(learner, module);
        moduleFile(module, "Facilitator Guide", 3, 1, true);
        document(learner, PoeDocumentType.CV, ReviewStatus.ACCEPTED, 1);
        SubmissionSession session = session(module, "Task 1", LocalDateTime.now().minusDays(1));
        submit(learner, session, LocalDateTime.now(), "Well done", "MODERATOR", FeedbackVisibility.INTERNAL);

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        try (ZipFile zip = new ZipFile(new File(job.getResultPublicId()))) {
            List<String> names = zip.stream().map(ZipEntry::getName).toList();
            assertThat(names).anyMatch(n -> n.contains("1. PERSONAL DETAILS") && n.contains("CV"));
            assertThat(names).anyMatch(n -> n.contains("3. ASSESSMENT GUIDELINES"));
            assertThat(names).anyMatch(n -> n.contains("4. ASSESSMENT ACTIVITIES"));
            assertThat(names).anyMatch(n -> n.contains("5. FEEDBACK") && n.endsWith(".txt"));
            assertThat(names).contains("00_INDEX.pdf", "SIGNATURES.csv");
        }
    }

    @Test
    @DisplayName("An INTERNAL, unpublished moderator report is in the zip — the one place it is allowed to be")
    void internalModeratorReportAppearsInExport() throws Exception {
        Learnership learnership = learnership("Internal");
        Learner learner = learner(learnership, "A", LocalDateTime.now().minusDays(10));
        Module module = module(learnership, "CORE");
        enrol(learner, module);
        SubmissionSession session = session(module, "Moderated Task", LocalDateTime.now().minusDays(1));
        Submission submission = submit(learner, session, LocalDateTime.now(),
                "This assessor's marking was too lenient on section 2.", "MODERATOR", FeedbackVisibility.INTERNAL);
        // Never published, and the learner would never see it via isMarkingVisibleToLearner().
        assertThat(submission.isMarkingVisibleToLearner()).isFalse();

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        String feedbackText = readEntryContaining(job.getResultPublicId(), "5. FEEDBACK");
        assertThat(feedbackText).contains("too lenient on section 2");
        assertThat(feedbackText).contains("INTERNAL");
    }

    @Test
    @DisplayName("Every version of a document is exported, not only the current one")
    void everyDocumentVersionIsExported() throws Exception {
        Learnership learnership = learnership("Versions");
        Learner learner = learner(learnership, "A", LocalDateTime.now());
        document(learner, PoeDocumentType.ID_COPY, ReviewStatus.REJECTED, 1);
        document(learner, PoeDocumentType.ID_COPY, ReviewStatus.ACCEPTED, 2);

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        try (ZipFile zip = new ZipFile(new File(job.getResultPublicId()))) {
            List<String> names = zip.stream().map(ZipEntry::getName).toList();
            assertThat(names).anyMatch(n -> n.contains("_v1"));
            assertThat(names).anyMatch(n -> n.contains("_v2"));
        }
    }

    @Test
    @DisplayName("A resubmission produces two versioned entries, both present")
    void resubmissionProducesTwoVersions() throws Exception {
        Learnership learnership = learnership("Resubmit");
        Learner learner = learner(learnership, "A", LocalDateTime.now().minusDays(10));
        Module module = module(learnership, "CORE");
        enrol(learner, module);
        SubmissionSession session = session(module, "Twice", LocalDateTime.now().minusDays(1));
        submit(learner, session, null, null, null, null);
        submit(learner, session, null, null, null, null);

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        try (ZipFile zip = new ZipFile(new File(job.getResultPublicId()))) {
            long activityEntries = zip.stream()
                    .filter(e -> e.getName().contains("4. ASSESSMENT ACTIVITIES"))
                    .count();
            assertThat(activityEntries).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Two learners with the same full name get distinct, non-colliding folders")
    void sameNameLearnersDoNotCollide() throws Exception {
        Learnership learnership = learnership("Namesakes");
        Learner one = learnerNamed(learnership, "Thabo Mokoena", "A");
        Learner two = learnerNamed(learnership, "Thabo Mokoena", "A");
        document(one, PoeDocumentType.CV, ReviewStatus.ACCEPTED, 1);
        document(two, PoeDocumentType.CV, ReviewStatus.ACCEPTED, 1);

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        try (ZipFile zip = new ZipFile(new File(job.getResultPublicId()))) {
            List<String> topFolders = zip.stream()
                    .map(ZipEntry::getName)
                    .filter(n -> n.contains("/")) // exclude 00_INDEX.pdf and SIGNATURES.csv, which sit at the root
                    .map(n -> n.split("/")[0])
                    .distinct().toList();
            assertThat(topFolders).hasSize(2);
            assertThat(topFolders).anyMatch(f -> f.contains(one.getLearnerCode()));
            assertThat(topFolders).anyMatch(f -> f.contains(two.getLearnerCode()));
        }
    }

    @Test
    @DisplayName("A section export leaves out personal documents and other categories' modules")
    void sectionExportIsNarrow() throws Exception {
        Learnership learnership = learnership("Narrow");
        Learner learner = learner(learnership, "A", LocalDateTime.now().minusDays(10));
        document(learner, PoeDocumentType.CV, ReviewStatus.ACCEPTED, 1);
        Module coreModule = module(learnership, "CORE");
        Module electiveModule = module(learnership, "ELECTIVE");
        enrol(learner, coreModule);
        enrol(learner, electiveModule);
        moduleFile(coreModule, "Core Guide", 3, 1, true);
        moduleFile(electiveModule, "Elective Guide", 3, 1, true);

        Category coreCategory = coreModule.getCategory();
        ExportJob job = runToCompletion(ADMIN, request("SECTION", learnership.getId(), null, coreCategory.getId()));

        try (ZipFile zip = new ZipFile(new File(job.getResultPublicId()))) {
            List<String> names = zip.stream().map(ZipEntry::getName).toList();
            assertThat(names).noneMatch(n -> n.contains("1. PERSONAL DETAILS"));
            assertThat(names).anyMatch(n -> n.contains("Core Guide"));
            assertThat(names).noneMatch(n -> n.contains("Elective Guide"));
        }
    }

    @Test
    @DisplayName("An unreadable stored file is skipped, not fatal — the job still completes")
    void unreadableFileDoesNotFailTheJob() {
        Learnership learnership = learnership("Broken");
        Learner learner = learner(learnership, "A", LocalDateTime.now());
        LearnerDocument doc = document(learner, PoeDocumentType.CV, ReviewStatus.ACCEPTED, 1);
        doc.setFilePath("private-uploads/documents/this-file-does-not-exist.pdf");
        documentRepository.save(doc);

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getFileCount()).isZero();
    }

    @Test
    @DisplayName("An empty scope still completes, with just the index and manifest")
    void emptyScopeStillCompletes() throws Exception {
        Learnership learnership = learnership("Empty");

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getLearnerCount()).isZero();
        try (ZipFile zip = new ZipFile(new File(job.getResultPublicId()))) {
            List<String> names = zip.stream().map(ZipEntry::getName).toList();
            assertThat(names).containsExactlyInAnyOrder("00_INDEX.pdf", "SIGNATURES.csv");
        }
    }

    @Test
    @DisplayName("SIGNATURES.csv carries the expected header and no data rows")
    void signaturesCsvHasHeaderOnly() throws Exception {
        Learnership learnership = learnership("Sig");
        learner(learnership, "A", LocalDateTime.now());

        ExportJob job = runToCompletion(ADMIN, request("LEARNERSHIP", learnership.getId(), null, null));

        try (ZipFile zip = new ZipFile(new File(job.getResultPublicId()))) {
            ZipEntry entry = zip.getEntry("SIGNATURES.csv");
            String content = new String(zip.getInputStream(entry).readAllBytes());
            assertThat(content).contains("verification_code,signable_type,signable_id");
            long dataLines = content.lines().filter(l -> !l.startsWith("#") && !l.startsWith("verification_code")).count();
            assertThat(dataLines).isZero();
        }
    }

    // ------------------------------------------------------------------ fixture

    private CreateExportRequest request(String scopeType, Long learnershipId, Long learnerId, Long categoryId) {
        return CreateExportRequest.builder()
                .scopeType(scopeType).learnershipId(learnershipId).learnerId(learnerId).categoryId(categoryId)
                .build();
    }

    private ExportJob runToCompletion(StaffPrincipal principal, CreateExportRequest req) {
        ExportJob job = exportService.createJob(principal, req);
        exportService.runExport(job.getId());
        return exportJobRepository.findById(job.getId()).orElseThrow();
    }

    private String readEntryContaining(String zipPath, String substring) throws Exception {
        try (ZipFile zip = new ZipFile(new File(zipPath))) {
            ZipEntry entry = zip.stream().filter(e -> e.getName().contains(substring)).findFirst().orElseThrow();
            return new String(zip.getInputStream(entry).readAllBytes());
        }
    }

    private Learnership learnership(String name) {
        return learnershipRepository.save(Learnership.builder().name(name + " " + System.nanoTime()).build());
    }

    private Category category(Learnership learnership, String type) {
        return categoryRepository.save(Category.builder().categoryType(type).learnership(learnership).build());
    }

    private Module module(Learnership learnership, String categoryType) {
        long unique = System.nanoTime();
        Category category = category(learnership, categoryType);
        return moduleRepository.save(Module.builder()
                .moduleName("Module " + unique).moduleCode("MC" + unique).category(category).build());
    }

    private ModuleFile moduleFile(Module module, String title, int poeSection, int version, boolean current) {
        return moduleFileRepository.save(ModuleFile.builder()
                .module(module).title(title).filePath(writeTempFile("guide " + title + " v" + version))
                .originalFilename(title + ".pdf").fileType("Guide")
                .poeSection(poeSection).version(version).current(current)
                .build());
    }

    private Learner learner(Learnership learnership, String cohort, LocalDateTime registeredAt) {
        return learnerNamed(learnership, "Learner " + System.nanoTime(), cohort, registeredAt);
    }

    private Learner learnerNamed(Learnership learnership, String fullName, String cohort) {
        return learnerNamed(learnership, fullName, cohort, LocalDateTime.now());
    }

    private Learner learnerNamed(Learnership learnership, String fullName, String cohort, LocalDateTime registeredAt) {
        long unique = System.nanoTime();
        return learnerRepository.save(Learner.builder()
                .fullName(fullName)
                .learnerCode("PE" + unique)
                .email("pe" + unique + "@example.com")
                .phoneNumber("0700000000")
                .cohort(cohort)
                .learnership(learnership)
                .createdAt(registeredAt)
                .modules(new HashSet<>())
                .build());
    }

    private void enrol(Learner learner, Module module) {
        Set<Module> modules = learner.getModules() == null ? new HashSet<>() : new HashSet<>(learner.getModules());
        modules.add(module);
        learner.setModules(modules);
        learnerRepository.saveAndFlush(learner);
    }

    private LearnerDocument document(Learner learner, PoeDocumentType type, ReviewStatus status, int version) {
        return documentRepository.save(LearnerDocument.builder()
                .learner(learner).documentType(type)
                .filePath(writeTempFile("document " + type + " v" + version))
                .originalFilename(type.name().toLowerCase() + ".pdf")
                .version(version).current(version > 0)
                .status(status).uploadedAt(LocalDateTime.now()).uploadedByRole("LEARNER")
                .build());
    }

    /**
     * A real file on disk, standing in for what Cloudinary would otherwise hold. The test
     * profile configures no Cloudinary account (there is none reachable from this environment),
     * so {@code StoredFileService} resolves any {@code filePath} as a local path — a fixture
     * that hands it a fabricated {@code lms_secure/...} string with nothing behind it would have
     * every file in the export "skipped as unreadable", which is a fixture bug, not the
     * behaviour under test.
     */
    private String writeTempFile(String content) {
        try {
            Path file = Files.createTempFile("poe-export-fixture-", ".pdf");
            Files.writeString(file, content);
            file.toFile().deleteOnExit();
            return file.toAbsolutePath().toString();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SubmissionSession session(Module module, String name, LocalDateTime endTime) {
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title(name).description("").dueDate(endTime).module(module).build());
        return sessionRepository.save(SubmissionSession.builder()
                .sessionName(name).assignment(assignment)
                .startTime(endTime.minusDays(7)).endTime(endTime)
                .status(SessionStatus.CLOSED).build());
    }

    private Submission submit(Learner learner, SubmissionSession session, LocalDateTime gradedAt,
                              String feedback, String gradedByRole, FeedbackVisibility visibility) {
        return submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath(writeTempFile("submission for " + learner.getLearnerCode())).originalFilename("work.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED)
                .gradedAt(gradedAt).feedback(feedback).gradedByRole(gradedByRole).gradedByName("Marker")
                .feedbackStatus(gradedAt != null ? FeedbackStatus.DRAFT : null)
                .feedbackVisibility(visibility)
                .build());
    }

    private Assessor assessor() {
        long unique = System.nanoTime();
        return assessorRepository.save(Assessor.builder()
                .fullName("Assessor " + unique).username("assessor" + unique).passwordHash("x").build());
    }

    private Moderator moderator() {
        long unique = System.nanoTime();
        return moderatorRepository.save(Moderator.builder()
                .fullName("Moderator " + unique).username("moderator" + unique).passwordHash("x").build());
    }
}
