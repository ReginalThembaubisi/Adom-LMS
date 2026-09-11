package com.example.learnerassignments.service;

import com.example.learnerassignments.exception.InvalidFileException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.LearnerDocumentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LearnershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 10: the legacy folder importer.
 *
 * The one property every test here ultimately checks, one way or another, is the brief's own
 * gate: nothing lands in {@code learner_documents} until a human calls {@code confirm}, and
 * calling {@code cancel} instead means it never does.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LegacyImportServiceTest {

    @Autowired LegacyImportService importService;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired LearnerDocumentRepository documentRepository;

    private static final String ADMIN = "admin";

    private Learner learner(String fullName, Learnership learnership) {
        long unique = System.nanoTime();
        return learnerRepository.save(Learner.builder()
                .fullName(fullName).learnerCode("LI" + unique)
                .email("li" + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>())
                .build());
    }

    private Learnership learnership() {
        return learnershipRepository.save(Learnership.builder().name("Legacy " + System.nanoTime()).build());
    }

    /** entries: path -> file content. A trailing "/" with no further use marks a directory. */
    private MockMultipartFile zipOf(String filename, java.util.Map<String, String> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", filename, "application/zip", baos.toByteArray());
    }

    // ================================================================== matching

    @Test
    @DisplayName("An exact folder-name match resolves with EXACT confidence")
    void exactMatch() throws Exception {
        Learnership ls = learnership();
        Learner amanda = learner("Amanda Mndawe", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Amanda Mndawe/1. PERSONAL DETAILS/CV.pdf", "%PDF cv bytes"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        List<LegacyImportEntry> entries = importService.entriesFor(batch.getId());
        assertThat(entries).hasSize(1);
        LegacyImportEntry entry = entries.get(0);
        assertThat(entry.getMatchConfidence()).isEqualTo(LegacyImportMatchConfidence.EXACT);
        assertThat(entry.getMatchedLearner().getId()).isEqualTo(amanda.getId());
        assertThat(entry.getDocumentType()).isEqualTo(PoeDocumentType.CV);
        assertThat(entry.isImportable()).isTrue();
    }

    @Test
    @DisplayName("A folder naming a subset of the learner's full name is a LIKELY match, not auto-picked as exact")
    void likelyMatch() throws Exception {
        Learnership ls = learnership();
        learner("Amanda Randy Mndawe", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "AMANDA MNDAWE/1. PERSONAL DETAILS/CV.pdf", "%PDF cv bytes"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        LegacyImportEntry entry = importService.entriesFor(batch.getId()).get(0);
        assertThat(entry.getMatchConfidence()).isEqualTo(LegacyImportMatchConfidence.LIKELY);
        assertThat(entry.getMatchedLearner()).isNotNull();
        assertThat(entry.isImportable()).isTrue();
    }

    @Test
    @DisplayName("Two learners who could both be the folder's name are UNMATCHED, never auto-picked")
    void ambiguousMatchIsUnmatched() throws Exception {
        Learnership ls = learnership();
        learner("Amanda Mndawe", ls);
        learner("Amanda Mndawe", ls); // same name on purpose

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Amanda Mndawe/1. PERSONAL DETAILS/CV.pdf", "%PDF cv bytes"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        LegacyImportEntry entry = importService.entriesFor(batch.getId()).get(0);
        assertThat(entry.getMatchConfidence()).isEqualTo(LegacyImportMatchConfidence.UNMATCHED);
        assertThat(entry.getMatchedLearner()).isNull();
        assertThat(entry.isImportable()).isFalse();
    }

    @Test
    @DisplayName("A folder matching no learner at all waits for a human — it is never auto-created")
    void noMatchIsUnmatchedAndNeverCreatesALearner() throws Exception {
        learnership(); // no learners at all
        long before = learnerRepository.count();

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "SOMEONE NOBODY KNOWS/1. PERSONAL DETAILS/CV.pdf", "%PDF cv bytes"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        LegacyImportEntry entry = importService.entriesFor(batch.getId()).get(0);
        assertThat(entry.getMatchConfidence()).isEqualTo(LegacyImportMatchConfidence.UNMATCHED);
        assertThat(entry.isImportable()).isFalse();
        assertThat(learnerRepository.count()).as("no learner was invented").isEqualTo(before);
    }

    // ================================================================== classification

    @Test
    @DisplayName("Section 1 and 2 filenames map to document types by keyword; unrecognised goes to OTHER")
    void filenameKeywordMapping() throws Exception {
        Learnership ls = learnership();
        learner("Keyword Learner", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Keyword Learner/1. PERSONAL DETAILS/My CV.pdf", "a",
                "Keyword Learner/1. PERSONAL DETAILS/ID Copy.pdf", "b",
                "Keyword Learner/2. QUALIFICATION DETAILS/Matric Certificate.pdf", "c",
                "Keyword Learner/2. QUALIFICATION DETAILS/Learnership Agreement.pdf", "d",
                "Keyword Learner/1. PERSONAL DETAILS/random_scan_007.pdf", "e"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        var byName = importService.entriesFor(batch.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getZipEntryPath().substring(e.getZipEntryPath().lastIndexOf('/') + 1),
                        e -> e.getDocumentType()));

        assertThat(byName.get("My CV.pdf")).isEqualTo(PoeDocumentType.CV);
        assertThat(byName.get("ID Copy.pdf")).isEqualTo(PoeDocumentType.ID_COPY);
        assertThat(byName.get("Matric Certificate.pdf")).isEqualTo(PoeDocumentType.MATRIC);
        assertThat(byName.get("Learnership Agreement.pdf")).isEqualTo(PoeDocumentType.AGREEMENT);
        assertThat(byName.get("random_scan_007.pdf")).isEqualTo(PoeDocumentType.OTHER);
    }

    @Test
    @DisplayName("A substring that only looks like \"ID\" inside a longer word is not misclassified")
    void idKeywordDoesNotFalsePositiveOnSubstring() throws Exception {
        Learnership ls = learnership();
        learner("Substring Learner", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Substring Learner/1. PERSONAL DETAILS/Validated_certificate.pdf", "a"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        assertThat(importService.entriesFor(batch.getId()).get(0).getDocumentType())
                .as("\"VALIDATED\" contains \"id\" as a substring but is not an ID document")
                .isEqualTo(PoeDocumentType.OTHER);
    }

    @Test
    @DisplayName("Case is normalised: cores, Cores and CORES all resolve the same way")
    void caseNormalisation() throws Exception {
        Learnership ls = learnership();
        learner("Case Learner", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Case Learner/cores/Module 1/brief.pdf", "a",
                "Case Learner/Cores/Module 1/brief2.pdf", "b",
                "Case Learner/CORES/Module 1/brief3.pdf", "c"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        assertThat(importService.entriesFor(batch.getId()))
                .as("all three case variants are recognised as out-of-scope module content, not one of them slipping into section 6")
                .allSatisfy(e -> {
                    assertThat(e.isImportable()).isFalse();
                    assertThat(e.getNote()).contains("outside this importer's scope");
                });
    }

    @Test
    @DisplayName("A file with no recognisable section folder lands in section 6 (OTHER), not dropped")
    void unmatchedFileLocationLandsInSectionSix() throws Exception {
        Learnership ls = learnership();
        learner("Loose File Learner", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Loose File Learner/some_random_doc.pdf", "a"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        LegacyImportEntry entry = importService.entriesFor(batch.getId()).get(0);
        assertThat(entry.getDocumentType()).isEqualTo(PoeDocumentType.OTHER);
        assertThat(entry.isImportable()).isTrue();
        assertThat(entry.getNote()).contains("not recognised");
    }

    @Test
    @DisplayName("An unsupported file type is flagged, not imported")
    void unsupportedExtensionIsFlagged() throws Exception {
        Learnership ls = learnership();
        learner("Bad Ext Learner", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Bad Ext Learner/1. PERSONAL DETAILS/cv.exe", "a"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);

        LegacyImportEntry entry = importService.entriesFor(batch.getId()).get(0);
        assertThat(entry.isImportable()).isFalse();
        assertThat(entry.getNote()).contains("Unsupported file type");
    }

    // ================================================================== single-learner zips

    @Test
    @DisplayName("A zip shaped like one learner's own folder requires learnerId, and is not guessed")
    void singleLearnerZipWithoutIdIsRejected() throws Exception {
        var zip = zipOf("legacy.zip", java.util.Map.of(
                "1. PERSONAL DETAILS/CV.pdf", "%PDF"));

        assertThatThrownBy(() -> importService.preview(zip, ADMIN, null))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("learnerId");
    }

    @Test
    @DisplayName("A single-learner zip with learnerId specified imports under that exact learner, confidence SPECIFIED")
    void singleLearnerZipWithId() throws Exception {
        Learnership ls = learnership();
        Learner solo = learner("Solo Learner", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "1. PERSONAL DETAILS/CV.pdf", "%PDF"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, solo.getId());

        LegacyImportEntry entry = importService.entriesFor(batch.getId()).get(0);
        assertThat(entry.getMatchConfidence()).isEqualTo(LegacyImportMatchConfidence.SPECIFIED);
        assertThat(entry.getMatchedLearner().getId()).isEqualTo(solo.getId());
    }

    // ================================================================== confirm

    @Test
    @DisplayName("Confirm writes only the importable, matched entries, through the real document-vault path")
    void confirmWritesOnlyImportableMatchedEntries() throws Exception {
        Learnership ls = learnership();
        Learner amanda = learner("Amanda Mndawe", ls);

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Amanda Mndawe/1. PERSONAL DETAILS/CV.pdf", "%PDF cv bytes",
                "Amanda Mndawe/2. QUALIFICATION DETAILS/Matric.pdf", "%PDF matric bytes",
                "Nobody Matching/1. PERSONAL DETAILS/CV.pdf", "%PDF orphan bytes"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);
        assertThat(batch.getImportableCount()).isEqualTo(2);

        LegacyImportBatch confirmed = importService.confirm(batch.getId(), ADMIN);

        assertThat(confirmed.getStatus()).isEqualTo(LegacyImportStatus.CONFIRMED);
        assertThat(confirmed.getImportedCount()).isEqualTo(2);
        assertThat(confirmed.getFailedCount()).isZero();

        List<LearnerDocument> docs = documentRepository.findByLearner_IdOrderByUploadedAtDesc(amanda.getId());
        assertThat(docs).hasSize(2);
        assertThat(docs).extracting(LearnerDocument::getDocumentType)
                .containsExactlyInAnyOrder(PoeDocumentType.CV, PoeDocumentType.MATRIC);
        assertThat(docs).allSatisfy(d -> assertThat(d.getUploadedByRole()).isEqualTo("ADMIN"));

        // The zip is consumed once confirmed — nothing left to import twice from.
        assertThat(confirmed.getStoredZipPath()).isNull();
    }

    @Test
    @DisplayName("Confirming twice is rejected, not silently re-imported")
    void confirmTwiceIsRejected() throws Exception {
        Learnership ls = learnership();
        learner("Once Learner", ls);
        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Once Learner/1. PERSONAL DETAILS/CV.pdf", "%PDF"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);
        importService.confirm(batch.getId(), ADMIN);

        assertThatThrownBy(() -> importService.confirm(batch.getId(), ADMIN))
                .isInstanceOf(InvalidFileException.class);
    }

    // ================================================================== cancel — the gate that matters most

    @Test
    @DisplayName("Cancelling a preview writes nothing: no LearnerDocument exists anywhere, and the zip is gone")
    void cancellingWritesNothing() throws Exception {
        Learnership ls = learnership();
        Learner amanda = learner("Amanda Mndawe", ls);
        long documentsBefore = documentRepository.count();

        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Amanda Mndawe/1. PERSONAL DETAILS/CV.pdf", "%PDF cv bytes",
                "Amanda Mndawe/2. QUALIFICATION DETAILS/Matric.pdf", "%PDF matric bytes"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);
        assertThat(batch.getImportableCount()).isEqualTo(2);
        String storedZipPath = batch.getStoredZipPath();
        assertThat(new File(storedZipPath)).as("the preview did store the zip while awaiting a decision").exists();

        LegacyImportBatch cancelled = importService.cancel(batch.getId(), ADMIN);

        assertThat(cancelled.getStatus()).isEqualTo(LegacyImportStatus.CANCELLED);
        assertThat(documentRepository.count()).as("cancelling wrote no documents at all").isEqualTo(documentsBefore);
        assertThat(documentRepository.findByLearner_IdOrderByUploadedAtDesc(amanda.getId())).isEmpty();
        assertThat(new File(storedZipPath)).as("the stored zip is deleted on cancel").doesNotExist();
        assertThat(cancelled.getStoredZipPath()).isNull();
    }

    @Test
    @DisplayName("Confirming a cancelled batch is rejected, not silently allowed after the fact")
    void cannotConfirmAfterCancel() throws Exception {
        Learnership ls = learnership();
        learner("Cancel Then Confirm", ls);
        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Cancel Then Confirm/1. PERSONAL DETAILS/CV.pdf", "%PDF"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);
        importService.cancel(batch.getId(), ADMIN);

        assertThatThrownBy(() -> importService.confirm(batch.getId(), ADMIN))
                .isInstanceOf(InvalidFileException.class);

        assertThat(documentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Cancelling twice is rejected")
    void cancelTwiceIsRejected() throws Exception {
        Learnership ls = learnership();
        learner("Double Cancel", ls);
        var zip = zipOf("legacy.zip", java.util.Map.of(
                "Double Cancel/1. PERSONAL DETAILS/CV.pdf", "%PDF"));
        LegacyImportBatch batch = importService.preview(zip, ADMIN, null);
        importService.cancel(batch.getId(), ADMIN);

        assertThatThrownBy(() -> importService.cancel(batch.getId(), ADMIN))
                .isInstanceOf(InvalidFileException.class);
    }
}
