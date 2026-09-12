package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.PoePortfolioDtos.FileKind;
import com.example.learnerassignments.dto.PoePortfolioDtos.LearnerPortfolioTree;
import com.example.learnerassignments.dto.PoePortfolioDtos.PortfolioCategoryFolder;
import com.example.learnerassignments.dto.PoePortfolioDtos.PortfolioFile;
import com.example.learnerassignments.dto.PoePortfolioDtos.PortfolioModuleFolder;
import com.example.learnerassignments.dto.PoePortfolioDtos.PortfolioSection;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.FeedbackStatus;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LearnerDocument;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.model.ModuleFile;
import com.example.learnerassignments.model.ReviewStatus;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.repository.LearnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a learner's portfolio on screen, in the same six-section shape the export zips.
 *
 * <p>Everything here reads from {@link PoeExportService#resolvePortfolio}, never from its own
 * queries. That method is the one place "what belongs where" is decided — pinned guide versions,
 * which submission is whose, per-session ordinals — and this class exists to shape that same
 * answer into a tree an admin can browse, not to compute a second one. Two resolutions of the
 * same question can drift; one resolution shown two ways cannot.
 *
 * <p>All six sections are always present, with a file count, even when nothing is in them — a
 * learner with no additional evidence still shows an empty "6. ADDITIONAL EVIDENCE" rather than
 * that section disappearing. Sections 3, 4 and 5 are further always broken into Fundamentals,
 * Cores and Electives, even when a category holds nothing for this learner, so the shape of the
 * qualification is visible whether or not this learner has work in every part of it. A module
 * whose category cannot be mapped to one of those three (deleted, or never set) still has to
 * appear somewhere a SETA auditor — and the export — would find it, so it gets its own
 * "Uncategorised" folder rather than being silently merged into one of the three or dropped.
 */
@Service
@RequiredArgsConstructor
public class PoePortfolioBrowserService {

    private final LearnerRepository learnerRepository;
    private final PoeExportService exportService;

    private static final List<String> ALWAYS_SHOWN_CATEGORIES = List.of("Fundamentals", "Cores", "Electives");

    @Transactional(readOnly = true)
    public LearnerPortfolioTree browse(Long learnerId) {
        Learner learner = learnerRepository.findById(learnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Learner not found"));

        PoeExportService.LearnerPortfolio portfolio = exportService.resolvePortfolio(learner, null);

        Map<Integer, List<PortfolioFile>> flatBySection = new LinkedHashMap<>();
        for (LearnerDocument doc : portfolio.personalDocuments()) {
            if (doc.getDocumentType() == null) {
                continue;
            }
            int section = doc.getDocumentType().getPoeSection();
            int key = PoeExportService.SECTION_FOLDERS.containsKey(section) ? section : 6;
            flatBySection.computeIfAbsent(key, k -> new ArrayList<>()).add(documentFile(learner, doc));
        }

        // categoryName -> moduleId -> that module's files, for each of sections 3/4/5 separately
        // (a module can have a guide but nothing marked yet, or marked work but no guide on file).
        Map<String, LinkedHashMap<Long, List<PortfolioFile>>> section3 = newCategoryMap();
        Map<String, LinkedHashMap<Long, List<PortfolioFile>>> section4 = newCategoryMap();
        Map<String, LinkedHashMap<Long, List<PortfolioFile>>> section5 = newCategoryMap();
        Map<Long, Module> modulesById = new LinkedHashMap<>();

        for (PoeExportService.ModulePortfolioFolder mf : portfolio.moduleFolders()) {
            Module module = mf.module();
            modulesById.put(module.getId(), module);
            String category = mf.categoryFolder();

            // computeIfAbsent on the module id even with nothing to add yet: an empty list is
            // what makes the module's folder itself appear (with no files) rather than vanish
            // from a section it simply has nothing in.
            section3.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .computeIfAbsent(module.getId(), k -> new ArrayList<>());
            section4.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .computeIfAbsent(module.getId(), k -> new ArrayList<>());
            section5.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .computeIfAbsent(module.getId(), k -> new ArrayList<>());

            if (mf.guide() != null) {
                section3.get(category).get(module.getId()).add(guideFile(module, mf.guide()));
            }

            for (PoeExportService.SubmissionOnModule som : mf.submissions()) {
                section4.get(category).get(module.getId()).add(submissionFile(learner, som));

                Submission submission = som.submission();
                if (submission.getGradedAt() != null) {
                    List<PortfolioFile> feedbackFiles = section5.get(category).get(module.getId());
                    feedbackFiles.add(feedbackFile(learner, som));
                    if (submission.getMarkedFilePath() != null && !submission.getMarkedFilePath().isBlank()) {
                        feedbackFiles.add(markedCopyFile(learner, som));
                    }
                }
            }
        }

        List<PortfolioSection> sections = new ArrayList<>(6);
        sections.add(flatSection(1, flatBySection));
        sections.add(flatSection(2, flatBySection));
        sections.add(categorySection(3, section3, modulesById));
        sections.add(categorySection(4, section4, modulesById));
        sections.add(categorySection(5, section5, modulesById));
        sections.add(flatSection(6, flatBySection));

        int totalFiles = sections.stream().mapToInt(PortfolioSection::getFileCount).sum();

        return LearnerPortfolioTree.builder()
                .learnerId(learner.getId())
                .learnerCode(learner.getLearnerCode())
                .fullName(learner.getFullName())
                .sections(sections)
                .totalFiles(totalFiles)
                .build();
    }

    /** Seeded with the three categories that must show even when nothing is in them; anything
     *  else (namely "Uncategorised") is added lazily, only when a module actually needs it, and
     *  keeps insertion order after the fixed three. */
    private Map<String, LinkedHashMap<Long, List<PortfolioFile>>> newCategoryMap() {
        Map<String, LinkedHashMap<Long, List<PortfolioFile>>> map = new LinkedHashMap<>();
        for (String category : ALWAYS_SHOWN_CATEGORIES) {
            map.put(category, new LinkedHashMap<>());
        }
        return map;
    }

    private PortfolioSection flatSection(int number, Map<Integer, List<PortfolioFile>> filesBySection) {
        List<PortfolioFile> files = filesBySection.getOrDefault(number, List.of());
        return PortfolioSection.builder()
                .number(number)
                .name(PoeExportService.SECTION_FOLDERS.get(number))
                .fileCount(files.size())
                .files(files)
                .build();
    }

    private PortfolioSection categorySection(int number,
                                              Map<String, LinkedHashMap<Long, List<PortfolioFile>>> byCategory,
                                              Map<Long, Module> modulesById) {
        List<PortfolioCategoryFolder> categories = new ArrayList<>();
        int sectionFileCount = 0;
        for (Map.Entry<String, LinkedHashMap<Long, List<PortfolioFile>>> categoryEntry : byCategory.entrySet()) {
            List<PortfolioModuleFolder> modules = new ArrayList<>();
            int categoryFileCount = 0;
            for (Map.Entry<Long, List<PortfolioFile>> moduleEntry : categoryEntry.getValue().entrySet()) {
                Module module = modulesById.get(moduleEntry.getKey());
                modules.add(PortfolioModuleFolder.builder()
                        .moduleId(module.getId())
                        .moduleName(module.getModuleName())
                        .moduleCode(module.getModuleCode())
                        .files(moduleEntry.getValue())
                        .build());
                categoryFileCount += moduleEntry.getValue().size();
            }
            categories.add(PortfolioCategoryFolder.builder()
                    .name(categoryEntry.getKey())
                    .fileCount(categoryFileCount)
                    .modules(modules)
                    .build());
            sectionFileCount += categoryFileCount;
        }
        return PortfolioSection.builder()
                .number(number)
                .name(PoeExportService.SECTION_FOLDERS.get(number))
                .fileCount(sectionFileCount)
                .categories(categories)
                .build();
    }

    // ================================================================== file entries

    private PortfolioFile documentFile(Learner learner, LearnerDocument doc) {
        ReviewStatus status = doc.getStatus();
        return PortfolioFile.builder()
                .kind(FileKind.DOCUMENT)
                .canonicalFilename(exportService.canonicalDocumentFilename(learner, doc))
                .at(doc.getUploadedAt())
                .version(doc.getVersion())
                .reviewStatus(status == null ? ReviewStatus.PENDING.name() : status.name())
                .reviewNote(doc.getReviewNote())
                .reviewable(true)
                .documentId(doc.getId())
                .openUrl("/api/admin/documents/" + doc.getId() + "/view")
                .build();
    }

    private PortfolioFile guideFile(Module module, ModuleFile guide) {
        return PortfolioFile.builder()
                .kind(FileKind.GUIDE)
                .canonicalFilename(exportService.canonicalGuideFilename(module, guide))
                .at(guide.getCreatedAt())
                .version(guide.getVersion())
                .openUrl("/api/admin/module-files/" + guide.getId() + "/view")
                .build();
    }

    private PortfolioFile submissionFile(Learner learner, PoeExportService.SubmissionOnModule som) {
        Submission submission = som.submission();
        return PortfolioFile.builder()
                .kind(FileKind.SUBMISSION)
                .canonicalFilename(exportService.canonicalSubmissionFilename(
                        learner, som.sessionName(), som.ordinal(), submission))
                .at(submission.getSubmittedAt())
                .version(som.ordinal())
                .sessionName(som.sessionName())
                .openUrl("/api/submissions/" + submission.getId() + "/view")
                .build();
    }

    private PortfolioFile feedbackFile(Learner learner, PoeExportService.SubmissionOnModule som) {
        Submission submission = som.submission();
        String releaseTag = submission.getFeedbackStatus() == FeedbackStatus.PUBLISHED
                ? "released" : "DRAFT - NOT YET RELEASED";
        return PortfolioFile.builder()
                .kind(FileKind.FEEDBACK)
                .canonicalFilename(exportService.canonicalFeedbackFilename(learner, som.sessionName(), som.ordinal()))
                .at(submission.getGradedAt())
                .version(som.ordinal())
                .sessionName(som.sessionName())
                .feedbackText(exportService.renderFeedback(submission))
                .releaseStatus(releaseTag)
                .build();
    }

    private PortfolioFile markedCopyFile(Learner learner, PoeExportService.SubmissionOnModule som) {
        Submission submission = som.submission();
        return PortfolioFile.builder()
                .kind(FileKind.MARKED_COPY)
                .canonicalFilename(exportService.canonicalMarkedCopyFilename(learner, som.sessionName(), som.ordinal()))
                .at(submission.getGradedAt())
                .version(som.ordinal())
                .sessionName(som.sessionName())
                .openUrl("/api/submissions/" + submission.getId() + "/view?marked=true")
                .build();
    }
}
