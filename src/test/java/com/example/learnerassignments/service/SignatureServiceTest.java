package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.SignatureDtos.SignatureResponse;
import com.example.learnerassignments.dto.SignatureDtos.VerifyResponse;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 9: signing, revocation on supersession, and public verification.
 *
 * Fixtures build entities directly rather than going through the multipart upload endpoints —
 * the same lighter-weight style {@code FeedbackPublicationTest} uses — except where the test's
 * whole point is the upload/resubmit path itself (revocation on supersession), which has to go
 * through the real service methods to prove the hook actually fires.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SignatureServiceTest {

    @Autowired SignatureService signatureService;
    @Autowired SignatureEventRepository signatureEventRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired LearnerDocumentRepository documentRepository;
    @Autowired LearnerDocumentService learnerDocumentService;
    @Autowired SubmissionService submissionService;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "Correct-Horse-1";
    private static final String SPECIMEN = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    // ================================================================== fixtures

    private Learner learner(String tag) {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(Learnership.builder().name("SIG " + unique).build());
        return learnerRepository.save(Learner.builder()
                .fullName(tag + " Learner").learnerCode(tag + unique)
                .email(tag + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>())
                .passwordHash(passwordEncoder.encode(PW))
                .build());
    }

    private LearnerDocument document(Learner learner, String sha256) {
        return documentRepository.save(LearnerDocument.builder()
                .learner(learner).documentType(PoeDocumentType.CV)
                .filePath("lms_secure/doc").originalFilename("cv.pdf")
                .sha256(sha256).version(1).current(true).status(ReviewStatus.PENDING)
                .uploadedAt(LocalDateTime.now()).uploadedByRole("LEARNER")
                .build());
    }

    private record World(Learner learner, Long sessionId, Long submissionId) {}

    private World submissionWorld(String sha256) {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(Learnership.builder().name("SIGSUB " + unique).build());
        Category category = categoryRepository.save(
                Category.builder().categoryType("CORE").learnership(learnership).build());
        Module module = moduleRepository.save(Module.builder()
                .moduleName("SIG Module").moduleCode("SIG" + unique).category(category).build());
        Learner learner = learnerRepository.save(Learner.builder()
                .fullName("Sig Learner").learnerCode("SIGL" + unique)
                .email("sigl" + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>(Set.of(module)))
                .passwordHash(passwordEncoder.encode(PW))
                .build());
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Sig Task").description("")
                .dueDate(LocalDateTime.now().plusDays(7)).module(module).build());
        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Sig Session").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).build());
        Submission submission = submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath("lms_secure/sub").originalFilename("sub.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED)
                .sha256(sha256)
                .build());
        return new World(learner, session.getId(), submission.getId());
    }

    // ================================================================== signing

    @Test
    @DisplayName("Signing a document copies its hash onto the event and issues a verification code")
    void signingCopiesHashAndIssuesCode() {
        Learner learner = learner("A");
        LearnerDocument doc = document(learner, "abc123hash");

        SignatureResponse response = signatureService.signLearnerDocument(
                learner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");

        assertThat(response.getVerificationCode()).isNotBlank();
        assertThat(response.getSignerRole()).isEqualTo("LEARNER");
        assertThat(response.isRevoked()).isFalse();

        SignatureEvent event = signatureEventRepository.findById(response.getId()).orElseThrow();
        assertThat(event.getDocumentSha256()).isEqualTo("abc123hash");
        assertThat(event.getDeclarationText()).isNotBlank();
        assertThat(event.getSignedAt()).isNotNull();
    }

    @Test
    @DisplayName("Wrong password does not sign")
    void wrongPasswordRejected() {
        Learner learner = learner("B");
        LearnerDocument doc = document(learner, "hash2");

        assertThatThrownBy(() -> signatureService.signLearnerDocument(
                learner, doc.getId(), SPECIMEN, "not-the-password", "127.0.0.1", "JUnit"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(signatureEventRepository.findBySignableTypeAndSignableIdOrderBySignedAtDesc(
                SignableType.LEARNER_DOCUMENT, doc.getId())).isEmpty();
    }

    @Test
    @DisplayName("A learner cannot sign someone else's document — answered as not found")
    void crossLearnerSigningIsNotFound() {
        Learner owner = learner("C");
        Learner intruder = learner("D");
        LearnerDocument doc = document(owner, "hash3");

        assertThatThrownBy(() -> signatureService.signLearnerDocument(
                intruder, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Signing the same document twice is rejected")
    void doubleSignRejected() {
        Learner learner = learner("E");
        LearnerDocument doc = document(learner, "hash4");
        signatureService.signLearnerDocument(learner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> signatureService.signLearnerDocument(
                learner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A document with no recorded hash cannot be signed")
    void noHashCannotBeSigned() {
        Learner learner = learner("F");
        LearnerDocument doc = document(learner, null);

        assertThatThrownBy(() -> signatureService.signLearnerDocument(
                learner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A blank specimen is rejected")
    void blankSpecimenRejected() {
        Learner learner = learner("G");
        LearnerDocument doc = document(learner, "hash5");

        assertThatThrownBy(() -> signatureService.signLearnerDocument(
                learner, doc.getId(), "  ", PW, "127.0.0.1", "JUnit"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================================================================== public verification

    @Test
    @DisplayName("Verification shows only document type, signer role, date and hash match — nothing more")
    void verifyReturnsOnlyTheSafeFields() {
        Learner learner = learner("H");
        LearnerDocument doc = document(learner, "hash6");
        SignatureResponse signed = signatureService.signLearnerDocument(
                learner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");

        VerifyResponse verify = signatureService.verify(signed.getVerificationCode());

        assertThat(verify.getDocumentType()).isEqualTo("CV");
        assertThat(verify.getSignerRole()).isEqualTo("LEARNER");
        assertThat(verify.getSignedAt()).isNotNull();
        assertThat(verify.isHashMatches()).isTrue();
        assertThat(verify.isRevoked()).isFalse();
        assertThat(verify.isValid()).isTrue();
        // VerifyResponse's own field list is the enforcement: it has no learner name, no id
        // number, no learnerId, no file content field to accidentally populate.
    }

    @Test
    @DisplayName("An unknown verification code is not found")
    void unknownCodeIsNotFound() {
        assertThatThrownBy(() -> signatureService.verify("does-not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================== revocation

    @Test
    @DisplayName("Revoking sets revokedAt and flips verification to invalid, but the hash still matches")
    void revocationFlipsValidity() {
        Learner learner = learner("I");
        LearnerDocument doc = document(learner, "hash7");
        SignatureResponse signed = signatureService.signLearnerDocument(
                learner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");

        signatureService.revokeActiveSignature(SignableType.LEARNER_DOCUMENT, doc.getId(), "test revoke");

        VerifyResponse verify = signatureService.verify(signed.getVerificationCode());
        assertThat(verify.isRevoked()).isTrue();
        assertThat(verify.isHashMatches()).as("the row itself never changed").isTrue();
        assertThat(verify.isValid()).isFalse();
    }

    @Test
    @DisplayName("Uploading a new document version revokes the signature on the version it replaces")
    void newVersionRevokesOldSignature() throws Exception {
        Learner learner = learner("J");
        MockMultipartFile file1 = new MockMultipartFile("file", "cv1.pdf", "application/pdf",
                "%PDF version one".getBytes(StandardCharsets.UTF_8));
        LearnerDocument v1 = learnerDocumentService.upload(learner, PoeDocumentType.CV, file1, "LEARNER");

        SignatureResponse signed = signatureService.signLearnerDocument(
                learner, v1.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");
        assertThat(signatureEventRepository.findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(
                SignableType.LEARNER_DOCUMENT, v1.getId())).isPresent();

        MockMultipartFile file2 = new MockMultipartFile("file", "cv2.pdf", "application/pdf",
                "%PDF version two".getBytes(StandardCharsets.UTF_8));
        learnerDocumentService.upload(learner, PoeDocumentType.CV, file2, "LEARNER");

        assertThat(signatureEventRepository.findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(
                SignableType.LEARNER_DOCUMENT, v1.getId())).as("the old version's signature is now revoked").isEmpty();
        SignatureEvent reloaded = signatureEventRepository.findById(signed.getId()).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Resubmitting to the same session revokes the signature on the earlier submission")
    void resubmissionRevokesOldSignature() throws Exception {
        World w = submissionWorld("will-be-overwritten");
        // submitAssignment computes its own hash from the uploaded bytes, so sign the row it
        // actually creates rather than the placeholder built in submissionWorld — reuse the
        // fixture only for the learner and session.
        Submission first = submissionRepository.findById(w.submissionId()).orElseThrow();

        SignatureResponse signed = signatureService.signSubmission(
                w.learner(), first.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");
        assertThat(signatureEventRepository.findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(
                SignableType.SUBMISSION, first.getId())).isPresent();

        MockMultipartFile resubmission = new MockMultipartFile("file", "resubmit.pdf", "application/pdf",
                "%PDF resubmission bytes".getBytes(StandardCharsets.UTF_8));
        submissionService.submitAssignment(w.learner().getLearnerCode(), w.sessionId(), resubmission);

        assertThat(signatureEventRepository.findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(
                SignableType.SUBMISSION, first.getId())).as("the superseded submission's signature is revoked").isEmpty();
        SignatureEvent reloaded = signatureEventRepository.findById(signed.getId()).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNotNull();
        assertThat(reloaded.getRevokedReason()).contains("resubmission");
    }

    // ================================================================== certificate rendering

    @Test
    @DisplayName("The certificate worker renders a real PDF with the signature's own data and stores it")
    void certificateGenerationProducesAPdf() throws Exception {
        Learner learner = learner("K");
        LearnerDocument doc = document(learner, "hash-for-pdf");
        SignatureResponse signed = signatureService.signLearnerDocument(
                learner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");

        // Called directly (the synchronous half), not via stampAsync — the same reason
        // PoeExportServiceTest calls runExport rather than runExportAsync: this proves the
        // rendering logic works without racing this test's own uncommitted transaction across
        // a real thread pool. The actual @Async crossing is verified against a running
        // container, per the Phase 9 brief.
        signatureService.generateAndStoreCertificate(signed.getId());

        SignatureEvent event = signatureEventRepository.findById(signed.getId()).orElseThrow();
        assertThat(event.getStampedFilePath()).isNotBlank();

        File pdf = new File(event.getStampedFilePath());
        assertThat(pdf).exists();
        byte[] bytes = Files.readAllBytes(pdf.toPath());
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("The learner's own signature list resolves; another learner's does not")
    void ownershipOnRead() {
        Learner owner = learner("L");
        Learner other = learner("M");
        LearnerDocument doc = document(owner, "hash8");
        SignatureResponse signed = signatureService.signLearnerDocument(
                owner, doc.getId(), SPECIMEN, PW, "127.0.0.1", "JUnit");

        assertThat(signatureService.requireOwn(signed.getId(), owner.getId()).getId()).isEqualTo(signed.getId());
        assertThatThrownBy(() -> signatureService.requireOwn(signed.getId(), other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
