package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.PoeExportDtos.CreateExportRequest;
import com.example.learnerassignments.dto.PoePortfolioDtos.FileKind;
import com.example.learnerassignments.dto.PoePortfolioDtos.LearnerPortfolioTree;
import com.example.learnerassignments.dto.PoePortfolioDtos.PortfolioCategoryFolder;
import com.example.learnerassignments.dto.PoePortfolioDtos.PortfolioFile;
import com.example.learnerassignments.dto.PoePortfolioDtos.PortfolioSection;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.example.learnerassignments.security.StaffPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The portfolio browser, tested against the requirement that actually matters: outside sections 3
 * and 4, it must never disagree with the export. Every test here either inspects the tree on its
 * own terms (all six sections present, the three categories always there under 3/4/5) or builds a
 * rich fixture, exports it, browses it, and diffs the two — the same check an admin could do by
 * hand, run as a test so it runs on every change instead of once.
 *
 * <p>Sections 3 (guide) and 4 (raw submission) are the one deliberate exception: {@link
 * PoeExportService} excludes both from the zip (see its {@code EXCLUDED_SECTION_NUMBERS}), while
 * this service's own tree — what {@code GET /api/admin/poe/portfolio/learners/{id}} returns —
 * still resolves and returns them in full. It is {@code PoePortfolioBrowser.jsx}'s own {@code
 * HIDDEN_SECTION_NUMBERS} that hides them on screen, not this backend. {@link
 * #matchesTheExportedZipExactly} asserts that narrower relationship, not byte-for-byte parity.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PoePortfolioBrowserServiceTest {

    @Autowired PoePortfolioBrowserService browserService;
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

    private static final StaffPrincipal ADMIN = StaffPrincipal.admin(1L, "admin");

    @Test
    @DisplayName("All six sections appear for a learner with nothing on record, each with a zero file count")
    void allSixSectionsAlwaysAppear() {
        Learnership learnership = learnership("Empty");
        Learner learner = learner(learnership);

        LearnerPortfolioTree tree = browserService.browse(learner.getId());

        assertThat(tree.getSections()).hasSize(6);
        assertThat(tree.getSections()).extracting(PortfolioSection::getNumber)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(tree.getSections()).allSatisfy(s -> assertThat(s.getFileCount()).isZero());
        assertThat(tree.getTotalFiles()).isZero();
    }

    @Test
    @DisplayName("Sections 3, 4 and 5 always show Fundamentals, Cores and Electives, even empty")
    void categorySubfoldersAlwaysShown() {
        Learnership learnership = learnership("OneCategory");
        Learner learner = learner(learnership);
        Module core = module(learnership, "CORE");
        enrol(learner, core);
        moduleFile(core, "Core Guide", 1, true);

        LearnerPortfolioTree tree = browserService.browse(learner.getId());

        for (int sectionNumber : List.of(3, 4, 5)) {
            PortfolioSection section = sectionNumber(tree, sectionNumber);
            assertThat(section.getCategories()).extracting(PortfolioCategoryFolder::getName)
                    .contains("Fundamentals", "Cores", "Electives");
            PortfolioCategoryFolder fundamentals = category(section, "Fundamentals");
            assertThat(fundamentals.getModules()).isEmpty();
            assertThat(fundamentals.getFileCount()).isZero();
        }
        PortfolioCategoryFolder cores = category(sectionNumber(tree, 3), "Cores");
        assertThat(cores.getModules()).hasSize(1);
        assertThat(cores.getModules().get(0).getFiles()).hasSize(1);
    }

    @Test
    @DisplayName("A document's canonical filename and review status match what LearnerDocumentService itself records")
    void documentReviewStatusMatchesSource() {
        Learnership learnership = learnership("DocStatus");
        Learner learner = learner(learnership);
        LearnerDocument doc = document(learner, PoeDocumentType.CV, ReviewStatus.REJECTED, 1, "Blurry scan");

        LearnerPortfolioTree tree = browserService.browse(learner.getId());

        PortfolioFile file = sectionNumber(tree, 1).getFiles().get(0);
        assertThat(file.getKind()).isEqualTo(FileKind.DOCUMENT);
        assertThat(file.getCanonicalFilename()).isEqualTo(exportService.canonicalDocumentFilename(learner, doc));
        assertThat(file.getReviewStatus()).isEqualTo("REJECTED");
        assertThat(file.getReviewNote()).isEqualTo("Blurry scan");
        assertThat(file.isReviewable()).isTrue();
        assertThat(file.getDocumentId()).isEqualTo(doc.getId());
        assertThat(file.getOpenUrl()).isEqualTo("/api/admin/documents/" + doc.getId() + "/view");
    }

    @Test
    @DisplayName("On-screen structure matches a zip exported for the same learner, outside the sections the export deliberately excludes")
    void matchesTheExportedZipExactly() throws Exception {
        Learnership learnership = learnership("Parity");
        Learner learner = learner(learnership);
        document(learner, PoeDocumentType.CV, ReviewStatus.ACCEPTED, 1, null);
        document(learner, PoeDocumentType.ID_COPY, ReviewStatus.PENDING, 1, null);
        document(learner, PoeDocumentType.OTHER, ReviewStatus.PENDING, 1, null);

        Module module = module(learnership, "CORE");
        enrol(learner, module);
        moduleFile(module, "Facilitator Guide", 1, true);

        SubmissionSession session = session(module, "Task 1", LocalDateTime.now().minusDays(1));
        submit(learner, session, null); // first attempt, ungraded
        Submission graded = submit(learner, session, LocalDateTime.now()); // resubmission, graded + marked
        graded.setMarkedFilePath(writeTempFile("marked copy"));
        submissionRepository.save(graded);

        ExportJob job = runToCompletion(request(learnership.getId()));
        Set<String> zipFilenames = zipBasenames(job.getResultPublicId());

        LearnerPortfolioTree tree = browserService.browse(learner.getId());
        Set<String> browserFilenames = allCanonicalFilenames(tree, Set.of());
        Set<String> browserFilenamesOutsideExcludedSections = allCanonicalFilenames(tree, Set.of(3, 4));

        assertThat(browserFilenames).isNotEmpty();
        // The zip and the browser agree everywhere except sections 3/4 -- PoeExportService's own
        // EXCLUDED_SECTION_NUMBERS -- which the export leaves out entirely while the browser
        // still resolves and returns them in full (see this class's own doc comment).
        assertThat(browserFilenamesOutsideExcludedSections).isEqualTo(zipFilenames);
        assertThat(browserFilenames)
                .as("the browser still shows the guide and the raw submission the zip leaves out")
                .isNotEqualTo(zipFilenames)
                .anyMatch(name -> name.contains("Facilitator Guide"))
                .anyMatch(name -> name.contains("Task"));
    }

    @Test
    @DisplayName("The pinned guide version shown on screen is the one the export would include")
    void pinnedGuideVersionMatchesExport() throws Exception {
        Learnership learnership = learnership("Pinned");
        Learner learner = learner(learnership);
        Module module = module(learnership, "CORE");
        enrol(learner, module);
        ModuleFile v1 = moduleFile(module, "Guide", 1, false);
        moduleFile(module, "Guide", 2, true); // current, but not what this learner worked from

        SubmissionSession session = session(module, "Pinned Task", LocalDateTime.now().minusDays(1));
        Submission submission = submit(learner, session, null);
        submission.setGuideVersionId(v1.getId());
        submissionRepository.save(submission);

        LearnerPortfolioTree tree = browserService.browse(learner.getId());
        PortfolioFile guideFile = category(sectionNumber(tree, 3), "Cores").getModules().get(0).getFiles().get(0);

        assertThat(guideFile.getVersion()).isEqualTo(1);
        assertThat(guideFile.getCanonicalFilename()).isEqualTo(exportService.canonicalGuideFilename(module, v1));
        assertThat(guideFile.getOpenUrl()).isEqualTo("/api/admin/module-files/" + v1.getId() + "/view");
    }

    // ------------------------------------------------------------------ assertions helpers

    private PortfolioSection sectionNumber(LearnerPortfolioTree tree, int number) {
        return tree.getSections().stream().filter(s -> s.getNumber() == number).findFirst().orElseThrow();
    }

    private PortfolioCategoryFolder category(PortfolioSection section, String name) {
        return section.getCategories().stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    /** Every canonical filename in the tree, skipping any section whose number is in {@code excludedSectionNumbers}. */
    private Set<String> allCanonicalFilenames(LearnerPortfolioTree tree, Set<Integer> excludedSectionNumbers) {
        Set<String> names = new HashSet<>();
        for (PortfolioSection section : tree.getSections()) {
            if (excludedSectionNumbers.contains(section.getNumber())) {
                continue;
            }
            if (section.getFiles() != null) {
                section.getFiles().forEach(f -> names.add(f.getCanonicalFilename()));
            }
            if (section.getCategories() != null) {
                section.getCategories().forEach(c -> c.getModules().forEach(m ->
                        m.getFiles().forEach(f -> names.add(f.getCanonicalFilename()))));
            }
        }
        return names;
    }

    private Set<String> zipBasenames(String zipPath) throws Exception {
        try (ZipFile zip = new ZipFile(new File(zipPath))) {
            return zip.stream()
                    .map(ZipEntry::getName)
                    .filter(n -> !n.equals("00_INDEX.pdf") && !n.equals("SIGNATURES.csv"))
                    .map(n -> n.substring(n.lastIndexOf('/') + 1))
                    .collect(Collectors.toSet());
        }
    }

    // ------------------------------------------------------------------ fixture

    private CreateExportRequest request(Long learnershipId) {
        return CreateExportRequest.builder().scopeType("LEARNERSHIP").learnershipId(learnershipId).build();
    }

    private ExportJob runToCompletion(CreateExportRequest req) {
        ExportJob job = exportService.createJob(ADMIN, req);
        exportService.runExport(job.getId());
        return exportJobRepository.findById(job.getId()).orElseThrow();
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

    private ModuleFile moduleFile(Module module, String title, int version, boolean current) {
        return moduleFileRepository.save(ModuleFile.builder()
                .module(module).title(title).filePath(writeTempFile("guide " + title + " v" + version))
                .originalFilename(title + ".pdf").fileType("Guide")
                .poeSection(3).version(version).current(current)
                .build());
    }

    private Learner learner(Learnership learnership) {
        long unique = System.nanoTime();
        return learnerRepository.save(Learner.builder()
                .fullName("Learner " + unique).learnerCode("PB" + unique)
                .email("pb" + unique + "@example.com").phoneNumber("0700000000")
                .cohort("A").learnership(learnership).createdAt(LocalDateTime.now())
                .modules(new HashSet<>()).build());
    }

    private void enrol(Learner learner, Module module) {
        Set<Module> modules = learner.getModules() == null ? new HashSet<>() : new HashSet<>(learner.getModules());
        modules.add(module);
        learner.setModules(modules);
        learnerRepository.saveAndFlush(learner);
    }

    private LearnerDocument document(Learner learner, PoeDocumentType type, ReviewStatus status, int version, String note) {
        return documentRepository.save(LearnerDocument.builder()
                .learner(learner).documentType(type)
                .filePath(writeTempFile("document " + type + " v" + version))
                .originalFilename(type.name().toLowerCase() + ".pdf")
                .version(version).current(true)
                .status(status).reviewNote(note).uploadedAt(LocalDateTime.now()).uploadedByRole("LEARNER")
                .build());
    }

    private String writeTempFile(String content) {
        try {
            Path file = Files.createTempFile("poe-portfolio-fixture-", ".pdf");
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

    private Submission submit(Learner learner, SubmissionSession session, LocalDateTime gradedAt) {
        return submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath(writeTempFile("submission for " + learner.getLearnerCode())).originalFilename("work.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED)
                .gradedAt(gradedAt).feedback(gradedAt != null ? "Well done." : null)
                .gradedByRole(gradedAt != null ? "FACILITATOR" : null)
                .gradedByName(gradedAt != null ? "Marker" : null)
                .feedbackStatus(gradedAt != null ? FeedbackStatus.DRAFT : null)
                .feedbackVisibility(FeedbackVisibility.LEARNER)
                .build());
    }
}
