package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The on-screen portfolio browser: the same six-section PoE structure the export zips, rendered
 * as a tree instead of written to disk.
 *
 * <p>Nothing here is computed independently of the export. Every file on this tree comes from
 * {@code PoeExportService.resolvePortfolio}, the same resolution {@code writeLearnerFolder} calls
 * to build the zip — see that method's doc for why. This class only shapes that resolution into
 * JSON; it never re-derives what belongs where.
 */
public class PoePortfolioDtos {

    public enum FileKind {
        DOCUMENT, GUIDE, SUBMISSION, FEEDBACK, MARKED_COPY
    }

    /**
     * One file (or, for FEEDBACK, one synthesised record) as it would appear in the export.
     *
     * <p>{@code openUrl} always points at an endpoint this application already authenticates and
     * re-serves through — never a direct storage link. FEEDBACK carries no {@code openUrl}: it is
     * not a stored file, it is text {@code renderFeedback} synthesises at read time, so its
     * content is shipped inline as {@code feedbackText} instead of a link to fetch.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PortfolioFile {
        private FileKind kind;
        private String canonicalFilename;
        private LocalDateTime at;
        private Integer version;

        /** Set only for DOCUMENT: PENDING / ACCEPTED / REJECTED. Null where no review applies. */
        private String reviewStatus;
        private String reviewNote;
        /** True only for DOCUMENT — the only kind with an accept/reject action. */
        private boolean reviewable;
        /** The id to PUT /api/admin/documents/{id}/review against, set only for DOCUMENT. */
        private Long documentId;

        /** Fetch this with the caller's own auth header and render from a blob URL. Null for FEEDBACK. */
        private String openUrl;

        /** Set only for FEEDBACK: the rendered marking record — outcome, marks, written feedback. */
        private String feedbackText;
        /** Set only for FEEDBACK: "released" or "DRAFT - NOT YET RELEASED", as the export's index shows it. */
        private String releaseStatus;

        /** Set for SUBMISSION, FEEDBACK and MARKED_COPY: which session this belongs to. */
        private String sessionName;
    }

    /** One module's evidence within one category, for sections 3, 4 and 5. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PortfolioModuleFolder {
        private Long moduleId;
        private String moduleName;
        private String moduleCode;
        private List<PortfolioFile> files;
    }

    /** Fundamentals / Cores / Electives — always present under sections 3, 4 and 5, even empty. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PortfolioCategoryFolder {
        private String name;
        private int fileCount;
        private List<PortfolioModuleFolder> modules;
    }

    /**
     * One of the six numbered PoE sections. Always present, even with nothing in it.
     *
     * <p>Sections 1, 2 and 6 are flat: {@code files} is populated and {@code categories} is null.
     * Sections 3, 4 and 5 are grouped by category: {@code categories} is populated (Fundamentals,
     * Cores, Electives — always all three, plus "Uncategorised" if any module actually needs it,
     * exactly as the export would fold it in) and {@code files} is null.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PortfolioSection {
        private int number;
        private String name;
        private int fileCount;
        private List<PortfolioFile> files;
        private List<PortfolioCategoryFolder> categories;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LearnerPortfolioTree {
        private Long learnerId;
        private String learnerCode;
        private String fullName;
        private List<PortfolioSection> sections;
        private int totalFiles;
    }
}
