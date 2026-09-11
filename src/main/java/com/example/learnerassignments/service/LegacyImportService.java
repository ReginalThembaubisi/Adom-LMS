package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.LegacyImportDtos.BatchResponse;
import com.example.learnerassignments.dto.LegacyImportDtos.EntryResponse;
import com.example.learnerassignments.exception.InvalidFileException;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LegacyImportBatch;
import com.example.learnerassignments.model.LegacyImportEntry;
import com.example.learnerassignments.model.LegacyImportMatchConfidence;
import com.example.learnerassignments.model.LegacyImportStatus;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.repository.LegacyImportBatchRepository;
import com.example.learnerassignments.repository.LegacyImportEntryRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Phase 10: importing historical PoE folders that predate this system.
 *
 * <p>Two rules make up the whole design:
 *
 * <p><strong>Nothing is written until a human confirms a preview.</strong> {@link #preview}
 * parses a zip and works out, file by file, which learner and which document type it thinks
 * each entry belongs to — but the only database writes it makes are to this feature's own
 * {@link LegacyImportBatch}/{@link LegacyImportEntry} bookkeeping tables, never to {@code
 * learner_documents}. Only {@link #confirm}, called explicitly against a {@code PREVIEWED}
 * batch, ever creates a {@link com.example.learnerassignments.model.LearnerDocument}. {@link
 * #cancel} deletes the stored zip and leaves the bookkeeping rows marked {@code CANCELLED} —
 * an audit trail that a preview happened, never a document.
 *
 * <p><strong>A folder name is never trusted into an auto-created learner or an auto-picked
 * match.</strong> {@link #matchLearner} answers {@code UNMATCHED} — not a guess — the moment
 * more than one learner could plausibly be "AMANDA MNDAWE", and nothing here has ever created a
 * {@link Learner} row. An unmatched folder waits for a human, exactly as the brief requires.
 *
 * <p>Imports go through {@link LearnerDocumentService#upload}, the same path a learner's own
 * browser upload takes — the same validation, the same hashing, the same storage resolution —
 * rather than a second, parallel write path that has never run in production.
 *
 * <p><strong>Scope, stated plainly:</strong> this importer only ever produces {@link
 * com.example.learnerassignments.model.LearnerDocument} rows (PoE sections 1, 2 and 6 — CV, ID,
 * matric, agreement, and everything else). Module-based content (sections 3-5: facilitator
 * guides, submissions, feedback, and the Fundamentals/Cores/Electives category folders) is
 * recognised so it is never misfiled into section 6, but it is reported as out of scope rather
 * than imported — see the class doc in the implementation brief for why.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyImportService {

    private final LegacyImportBatchRepository batchRepository;
    private final LegacyImportEntryRepository entryRepository;
    private final LearnerRepository learnerRepository;
    private final LearnerDocumentService learnerDocumentService;

    @Value("${file.submission-dir:private-uploads}")
    private String privateDir;

    private static final long MAX_ZIP_BYTES = 250L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "docx");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> SECTION1_TOKENS = Set.of("PERSONALDETAILS", "PERSONAL", "1");
    private static final Set<String> SECTION2_TOKENS = Set.of("QUALIFICATIONDETAILS", "QUALIFICATION", "2");
    private static final Set<String> SECTION6_TOKENS = Set.of("ADDITIONALEVIDENCE", "ADDITIONAL", "OTHER", "6");
    private static final Set<String> OUT_OF_SCOPE_TOKENS = Set.of(
            "ASSESSMENTGUIDELINES", "3", "ASSESSMENTACTIVITIES", "4", "FEEDBACK", "5",
            "CORE", "CORES", "FUNDAMENTAL", "FUNDAMENTALS", "ELECTIVE", "ELECTIVES");

    private enum PathSection { SECTION1, SECTION2, SECTION6, OUT_OF_SCOPE, UNRECOGNISED }

    // ================================================================== preview

    /**
     * Parses the zip and writes only the preview bookkeeping — never a document.
     *
     * @param singleLearnerId when the zip is one learner's own folder (no name to match), the
     *                        admin names them directly; every entry gets {@code SPECIFIED}
     *                        confidence. Null for a parent zip of many learner folders, matched
     *                        by folder name instead.
     */
    @Transactional
    public LegacyImportBatch preview(MultipartFile zip, String adminUsername, Long singleLearnerId) {
        if (zip == null || zip.isEmpty()) {
            throw new InvalidFileException("Choose a zip file to import.");
        }
        if (zip.getSize() > MAX_ZIP_BYTES) {
            throw new InvalidFileException("That zip is larger than 250 MB. Split it into smaller batches.");
        }
        String originalName = zip.getOriginalFilename() == null ? "import.zip" : zip.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new InvalidFileException("Only a .zip file is accepted.");
        }

        Learner specifiedLearner = null;
        if (singleLearnerId != null) {
            specifiedLearner = learnerRepository.findById(singleLearnerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Learner not found"));
        }

        LegacyImportBatch batch = batchRepository.save(LegacyImportBatch.builder()
                .adminUsername(adminUsername)
                .originalFilename(originalName)
                .status(LegacyImportStatus.PREVIEWED)
                .build());

        log.info("Legacy import {} started by {}: parsing \"{}\" ({} bytes).",
                batch.getId(), adminUsername, originalName, zip.getSize());

        Path storedZip = storeZip(zip, batch.getId());
        batch.setStoredZipPath(storedZip.toString());

        List<Learner> allLearners = specifiedLearner == null ? learnerRepository.findAll() : List.of();
        List<LegacyImportEntry> entries = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(storedZip.toFile())) {
            List<ZipEntry> fileEntries = zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> !isJunk(e.getName()))
                    .<ZipEntry>map(e -> e)
                    .toList();

            boolean singleLearnerMode = singleLearnerId != null || looksLikeSingleLearnerZip(fileEntries);
            if (singleLearnerMode && specifiedLearner == null) {
                throw new InvalidFileException(
                        "This zip looks like one learner's own folder (its top level is section "
                                + "folders, not learner names). Pass learnerId to say whose it is.");
            }

            for (ZipEntry entry : fileEntries) {
                entries.add(classify(batch, entry, zipFile, singleLearnerMode, specifiedLearner, allLearners));
            }
        } catch (IOException e) {
            // The DB row rolls back on its own — this method is @Transactional and both
            // exceptions below are unchecked — but the zip was already written to disk before
            // either of these could be known, and a disk write is not transactional. Leaving it
            // behind would be a small leak on every rejected upload, on the one instance where a
            // slow disk leak matters most.
            deleteQuietly(storedZip);
            throw new InvalidFileException("Could not read that zip file. Is it a valid, uncorrupted .zip?");
        } catch (InvalidFileException e) {
            deleteQuietly(storedZip);
            throw e;
        }

        entryRepository.saveAll(entries);

        batch.setEntryCount(entries.size());
        batch.setImportableCount((int) entries.stream().filter(LegacyImportEntry::isImportable).count());
        batch.setUnmatchedCount((int) entries.stream()
                .filter(e -> e.getMatchConfidence() == LegacyImportMatchConfidence.UNMATCHED).count());
        batchRepository.save(batch);

        log.info("Legacy import {} preview complete: {} file(s) found, {} importable, {} with no confident "
                        + "learner match. Nothing has been written to any learner's documents.",
                batch.getId(), batch.getEntryCount(), batch.getImportableCount(), batch.getUnmatchedCount());

        return batch;
    }

    /** A zip whose top level is section/category folders rather than learner-name folders. */
    private boolean looksLikeSingleLearnerZip(List<ZipEntry> fileEntries) {
        for (ZipEntry entry : fileEntries) {
            String[] segments = splitPath(entry.getName());
            if (segments.length == 0) {
                continue;
            }
            String top = normalizeSegment(segments[0]);
            if (SECTION1_TOKENS.contains(top) || SECTION2_TOKENS.contains(top) || SECTION6_TOKENS.contains(top)
                    || OUT_OF_SCOPE_TOKENS.contains(top) || segments.length == 1) {
                // segments.length == 1 means a loose file sits directly at the zip root, which
                // only happens inside a single learner's own folder — a parent-of-many zip
                // always has the learner folder as a directory, never a bare file at the top.
                return true;
            }
        }
        return false;
    }

    private LegacyImportEntry classify(LegacyImportBatch batch, ZipEntry entry, ZipFile zipFile,
                                        boolean singleLearnerMode, Learner specifiedLearner,
                                        List<Learner> allLearners) {
        String[] segments = splitPath(entry.getName());
        String filename = segments[segments.length - 1];
        String topFolder = singleLearnerMode ? null : segments.length > 0 ? segments[0] : null;
        String[] sectionSegments = sectionSegmentsOf(segments, singleLearnerMode);

        Learner matched;
        LegacyImportMatchConfidence confidence;
        if (singleLearnerMode) {
            matched = specifiedLearner;
            confidence = LegacyImportMatchConfidence.SPECIFIED;
        } else {
            MatchResult result = matchLearner(topFolder, allLearners);
            matched = result.learner();
            confidence = result.confidence();
        }

        PathSection section = classifyPathSection(Arrays.asList(sectionSegments));
        PoeDocumentType documentType = null;
        boolean importable;
        String note = null;

        switch (section) {
            case SECTION1, SECTION2 -> {
                documentType = classifyFilename(filename);
                importable = true;
            }
            case SECTION6 -> {
                documentType = PoeDocumentType.OTHER;
                importable = true;
            }
            case OUT_OF_SCOPE -> {
                importable = false;
                note = "Not imported: module or submission content is outside this importer's scope "
                        + "(personal documents only — sections 1, 2 and 6).";
            }
            default -> {
                documentType = PoeDocumentType.OTHER;
                importable = true;
                note = "Original folder location was not recognised; filed under Additional Evidence.";
            }
        }

        if (importable) {
            String extension = extensionOf(filename);
            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                importable = false;
                note = "Unsupported file type (" + (extension.isEmpty() ? "no extension" : "." + extension) + "); not imported.";
            } else if (entry.getSize() > MAX_FILE_BYTES) {
                importable = false;
                note = "Larger than 10 MB; not imported.";
            }
        }

        if (matched == null) {
            importable = false;
            note = confidence == LegacyImportMatchConfidence.UNMATCHED
                    ? "No confident learner match for folder \"" + topFolder + "\" — resolve manually."
                    : note;
        }

        return LegacyImportEntry.builder()
                .batch(batch)
                .zipEntryPath(entry.getName())
                .topFolder(topFolder)
                .matchedLearner(matched)
                .matchedLearnerName(matched == null ? null : matched.getFullName())
                .matchConfidence(confidence)
                .documentType(documentType)
                .importable(importable)
                .note(note)
                .fileSizeBytes(entry.getSize())
                .build();
    }

    // ================================================================== confirm

    /**
     * Writes every importable, matched entry through {@link LearnerDocumentService#upload}, the
     * same path a real learner upload takes. One bad file does not sink the batch — each write
     * is wrapped, a failure is recorded on that entry and counted, and the rest still import,
     * the same discipline {@code PoeExportService} applies to a bad file mid-export.
     */
    @Transactional
    public LegacyImportBatch confirm(Long batchId, String adminUsername) {
        LegacyImportBatch batch = requireBatch(batchId);
        if (batch.getStatus() != LegacyImportStatus.PREVIEWED) {
            throw new InvalidFileException("This import has already been " + batch.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }

        log.info("Legacy import {} confirm requested by {}: about to write {} importable file(s).",
                batchId, adminUsername, batch.getImportableCount());

        List<LegacyImportEntry> entries = entryRepository.findByBatch_IdOrderByIdAsc(batchId);
        int imported = 0;
        int failed = 0;

        try (ZipFile zipFile = new ZipFile(requireStoredZip(batch))) {
            for (LegacyImportEntry entry : entries) {
                if (!entry.isImportable() || entry.getMatchedLearner() == null) {
                    continue;
                }
                try {
                    ZipEntry zipEntry = zipFile.getEntry(entry.getZipEntryPath());
                    if (zipEntry == null) {
                        throw new IOException("Entry vanished from the zip: " + entry.getZipEntryPath());
                    }
                    byte[] bytes;
                    try (InputStream in = zipFile.getInputStream(zipEntry)) {
                        bytes = in.readAllBytes();
                    }
                    String filename = entry.getZipEntryPath().substring(entry.getZipEntryPath().lastIndexOf('/') + 1);
                    MultipartFile file = new ByteArrayMultipartFile(filename, null, bytes);

                    var document = learnerDocumentService.upload(
                            entry.getMatchedLearner(), entry.getDocumentType(), file, "ADMIN");
                    entry.setImportedDocumentId(document.getId());
                    imported++;
                } catch (Exception e) {
                    entry.setImportError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    failed++;
                    log.warn("Legacy import {}: could not import \"{}\": {}", batchId, entry.getZipEntryPath(), e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new InvalidFileException("Could not re-open the stored zip to import it. Try previewing again.");
        }

        entryRepository.saveAll(entries);

        batch.setStatus(LegacyImportStatus.CONFIRMED);
        batch.setImportedCount(imported);
        batch.setFailedCount(failed);
        batch.setConfirmedAt(java.time.LocalDateTime.now());
        batchRepository.save(batch);

        deleteQuietly(Paths.get(batch.getStoredZipPath()));
        batch.setStoredZipPath(null);
        batchRepository.save(batch);

        log.info("Legacy import {} confirmed by {}: {} file(s) imported, {} failed.",
                batchId, adminUsername, imported, failed);

        return batch;
    }

    // ================================================================== cancel

    /**
     * Writes nothing to any learner's documents — that never happened on this batch to begin
     * with. Deletes the stored zip and marks the batch cancelled; the preview rows stay, as a
     * record that an import was attempted and explicitly abandoned, not committed.
     */
    @Transactional
    public LegacyImportBatch cancel(Long batchId, String adminUsername) {
        LegacyImportBatch batch = requireBatch(batchId);
        if (batch.getStatus() != LegacyImportStatus.PREVIEWED) {
            throw new InvalidFileException("This import has already been " + batch.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }

        log.info("Legacy import {} cancel requested by {}. Nothing was written; deleting the stored zip.",
                batchId, adminUsername);

        if (batch.getStoredZipPath() != null) {
            deleteQuietly(Paths.get(batch.getStoredZipPath()));
        }
        batch.setStoredZipPath(null);
        batch.setStatus(LegacyImportStatus.CANCELLED);
        batch.setCancelledAt(java.time.LocalDateTime.now());
        batchRepository.save(batch);

        log.info("Legacy import {} cancelled: 0 documents written, 0 learners touched.", batchId);
        return batch;
    }

    // ================================================================== reading

    @Transactional(readOnly = true)
    public LegacyImportBatch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found"));
    }

    @Transactional(readOnly = true)
    public List<LegacyImportBatch> listBatches() {
        return batchRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<LegacyImportEntry> entriesFor(Long batchId) {
        return entryRepository.findByBatch_IdOrderByIdAsc(batchId);
    }

    public BatchResponse toResponse(LegacyImportBatch batch, List<LegacyImportEntry> entries) {
        return BatchResponse.builder()
                .id(batch.getId())
                .originalFilename(batch.getOriginalFilename())
                .status(batch.getStatus().name())
                .entryCount(batch.getEntryCount())
                .importableCount(batch.getImportableCount())
                .unmatchedCount(batch.getUnmatchedCount())
                .importedCount(batch.getImportedCount())
                .failedCount(batch.getFailedCount())
                .createdAt(batch.getCreatedAt())
                .confirmedAt(batch.getConfirmedAt())
                .cancelledAt(batch.getCancelledAt())
                .entries(entries == null ? List.of() : entries.stream().map(this::toResponse).toList())
                .build();
    }

    private EntryResponse toResponse(LegacyImportEntry entry) {
        return EntryResponse.builder()
                .id(entry.getId())
                .zipEntryPath(entry.getZipEntryPath())
                .topFolder(entry.getTopFolder())
                .matchedLearnerId(entry.getMatchedLearner() == null ? null : entry.getMatchedLearner().getId())
                .matchedLearnerName(entry.getMatchedLearnerName())
                .matchConfidence(entry.getMatchConfidence().name())
                .documentType(entry.getDocumentType() == null ? null : entry.getDocumentType().name())
                .documentLabel(entry.getDocumentType() == null ? null : entry.getDocumentType().getLabel())
                .importable(entry.isImportable())
                .note(entry.getNote())
                .fileSizeBytes(entry.getFileSizeBytes())
                .importedDocumentId(entry.getImportedDocumentId())
                .importError(entry.getImportError())
                .build();
    }

    // ================================================================== matching

    private record MatchResult(Learner learner, LegacyImportMatchConfidence confidence) {
        static MatchResult unmatched() {
            return new MatchResult(null, LegacyImportMatchConfidence.UNMATCHED);
        }
    }

    /**
     * "AMANDA MNDAWE" against a roster that includes "Amanda Randy Mndawe": every word in the
     * folder name has to appear in the learner's name, or vice versa. The moment more than one
     * learner fits — or none do — this answers {@code UNMATCHED} rather than guessing. Two
     * learners sharing a name is not a rare event in a cohort drawn from a small set of common
     * surnames, and picking one silently is exactly the mistake the brief calls out by name.
     */
    private MatchResult matchLearner(String folderName, List<Learner> allLearners) {
        if (folderName == null || folderName.isBlank()) {
            return MatchResult.unmatched();
        }
        Set<String> folderTokens = tokenize(folderName);
        if (folderTokens.isEmpty()) {
            return MatchResult.unmatched();
        }

        List<Learner> exact = allLearners.stream()
                .filter(l -> l.getFullName() != null && tokenize(l.getFullName()).equals(folderTokens))
                .toList();
        if (exact.size() == 1) {
            return new MatchResult(exact.get(0), LegacyImportMatchConfidence.EXACT);
        }

        List<Learner> likely = allLearners.stream()
                .filter(l -> l.getFullName() != null)
                .filter(l -> {
                    Set<String> learnerTokens = tokenize(l.getFullName());
                    return learnerTokens.containsAll(folderTokens) || folderTokens.containsAll(learnerTokens);
                })
                .toList();
        if (likely.size() == 1) {
            return new MatchResult(likely.get(0), LegacyImportMatchConfidence.LIKELY);
        }

        return MatchResult.unmatched();
    }

    private Set<String> tokenize(String name) {
        return Arrays.stream(name.trim().toUpperCase(Locale.ROOT).split("[^A-Z]+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    // ================================================================== filename classification

    /**
     * Filename keyword to {@link PoeDocumentType}, on whole tokens rather than raw substrings —
     * a substring check for "ID" would misfire on "VALIDATED_certificate.pdf" (contains "id" at
     * position 3), which is exactly the kind of silent misfile this importer exists to avoid.
     * Anything not recognised is {@code OTHER}, never guessed into one of the four named types.
     */
    private PoeDocumentType classifyFilename(String filename) {
        String base = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        Set<String> tokens = Arrays.stream(base.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (tokens.contains("MATRIC") || tokens.contains("MATRICULATION")) {
            return PoeDocumentType.MATRIC;
        }
        if (tokens.contains("AGREEMENT") || tokens.contains("LEARNERSHIP")) {
            return PoeDocumentType.AGREEMENT;
        }
        if (tokens.contains("CV") || tokens.contains("RESUME") || tokens.contains("CURRICULUM")) {
            return PoeDocumentType.CV;
        }
        if (tokens.contains("ID") || tokens.contains("IDCOPY") || tokens.contains("IDDOC")
                || tokens.contains("IDENTITY") || tokens.contains("IDNUMBER")) {
            return PoeDocumentType.ID_COPY;
        }
        return PoeDocumentType.OTHER;
    }

    // ================================================================== path helpers

    private PathSection classifyPathSection(List<String> pathSegments) {
        for (String segment : pathSegments) {
            String norm = normalizeSegment(segment);
            if (SECTION1_TOKENS.contains(norm)) return PathSection.SECTION1;
            if (SECTION2_TOKENS.contains(norm)) return PathSection.SECTION2;
            if (SECTION6_TOKENS.contains(norm)) return PathSection.SECTION6;
            if (OUT_OF_SCOPE_TOKENS.contains(norm)) return PathSection.OUT_OF_SCOPE;
        }
        return PathSection.UNRECOGNISED;
    }

    /** Strips a leading ordinal ("1. ", "2 - ") then collapses to bare uppercase letters and
     *  digits, so "1. PERSONAL DETAILS", "Personal Details" and "personaldetails" all compare
     *  equal, and "cores" / "Cores" / "CORES" all resolve to the same bucket. */
    private String normalizeSegment(String segment) {
        String s = segment.trim().replaceFirst("^\\d+[.\\-\\s]+", "");
        return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /**
     * The path segments between the top-level folder (skipped in "many learners" mode, since it
     * names the learner rather than a section) and the filename itself (never part of the
     * section search — a file is never mistaken for a folder it sits inside).
     */
    private String[] sectionSegmentsOf(String[] segments, boolean singleLearnerMode) {
        int start = singleLearnerMode ? 0 : Math.min(1, segments.length);
        int end = Math.max(start, segments.length - 1);
        return Arrays.copyOfRange(segments, start, end);
    }

    private String[] splitPath(String entryName) {
        return Arrays.stream(entryName.split("/"))
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    private boolean isJunk(String entryName) {
        return entryName.contains("__MACOSX") || entryName.endsWith(".DS_Store")
                || Arrays.stream(entryName.split("/")).anyMatch(s -> s.equals("..") || s.equals("."));
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ================================================================== storage

    private Path storeZip(MultipartFile zip, Long batchId) {
        try {
            Path dir = Paths.get(privateDir, "legacy-imports").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve("legacy_import_" + batchId + ".zip");
            try (InputStream in = zip.getInputStream()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new InvalidFileException("Could not store that zip file. Please try again.");
        }
    }

    private java.io.File requireStoredZip(LegacyImportBatch batch) {
        if (batch.getStoredZipPath() == null) {
            throw new InvalidFileException("The uploaded zip for this import is no longer available.");
        }
        java.io.File file = new java.io.File(batch.getStoredZipPath());
        if (!file.exists()) {
            throw new InvalidFileException("The uploaded zip for this import is no longer available.");
        }
        return file;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leaked temp file here is cleaned up by the OS eventually; not worth failing an
            // otherwise-successful confirm or cancel over.
        }
    }
}
