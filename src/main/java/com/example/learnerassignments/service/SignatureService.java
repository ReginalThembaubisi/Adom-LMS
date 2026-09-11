package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.SignatureDtos.SignatureResponse;
import com.example.learnerassignments.dto.SignatureDtos.VerifyResponse;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LearnerDocument;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.model.SignableType;
import com.example.learnerassignments.model.SignatureEvent;
import com.example.learnerassignments.model.Submission;
import com.example.learnerassignments.repository.LearnerDocumentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.SignatureEventRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Signing, verifying and revoking Phase 9 signature events.
 *
 * <p>Two rules run through this class, both carried over from Phase 8's exporter:
 *
 * <p><strong>Signing never fetches the target file.</strong> {@code sha256} is stored on
 * {@link Submission} and {@link LearnerDocument} at upload time for exactly this reason — the
 * hash copied onto a {@link SignatureEvent} comes from that column, not from re-reading bytes.
 * The certificate PDF this class generates is a self-contained proof page (declaration, signer
 * role, date, hash, a QR code to the verify page) rather than a stamp applied to the original
 * file, which means generating it does not need the file either, and the original evidence a
 * learner submitted is never rewritten by the act of signing it.
 *
 * <p><strong>The certificate is generated off the request thread.</strong> {@link #stampAsync}
 * runs on {@code signatureTaskExecutor} with no HTTP request bound to it, and calls back into
 * this bean through {@link #self} rather than directly — the same {@code @Lazy} self-injection
 * Phase 8 needed after discovering that an {@code @Async} method calling a {@code @Transactional}
 * one on the same object, from inside the same class, bypasses the Spring proxy that makes
 * either annotation do anything. That bug only ever showed up running in a real container.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureService {

    private final SignatureEventRepository signatureEventRepository;
    private final SubmissionRepository submissionRepository;
    private final LearnerDocumentRepository learnerDocumentRepository;
    private final LearnerRepository learnerRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final StoredFileService storedFileService;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private SignatureService self;

    @Value("${file.submission-dir:private-uploads}")
    private String privateDir;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    private static final String SUBMISSION_DECLARATION =
            "I confirm that this submission is my own work, submitted by me, for the purpose of "
                    + "this learnership's assessment. I understand that plagiarism or dishonesty "
                    + "may be dealt with under my learnership agreement.";

    private static final String LEARNER_DOCUMENT_DECLARATION =
            "I confirm that this document is a true and accurate copy supplied by me for the "
                    + "purposes of my Portfolio of Evidence.";

    /**
     * The exact wording the signing screen must show before a learner ticks the box — read
     * from the same constant {@link #createEvent} stores, so the two can never drift apart.
     */
    public String declarationTextFor(SignableType type) {
        return switch (type) {
            case SUBMISSION -> SUBMISSION_DECLARATION;
            case LEARNER_DOCUMENT -> LEARNER_DOCUMENT_DECLARATION;
        };
    }

    // ================================================================== signing

    @Transactional
    public SignatureResponse signSubmission(Learner learner, Long submissionId, String specimenImage,
                                             String password, String ipAddress, String userAgent) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));
        if (submission.getLearner() == null || !submission.getLearner().getId().equals(learner.getId())) {
            // Not this learner's submission — answered the same as one that does not exist.
            throw new ResourceNotFoundException("Submission not found");
        }
        if (submission.getSha256() == null || submission.getSha256().isBlank()) {
            throw new IllegalStateException("This submission has no recorded hash and cannot be signed.");
        }
        SignatureEvent event = createEvent(SignableType.SUBMISSION, submission.getId(), learner,
                submission.getSha256(), SUBMISSION_DECLARATION, specimenImage, password, ipAddress, userAgent);
        return toResponse(event);
    }

    @Transactional
    public SignatureResponse signLearnerDocument(Learner learner, Long documentId, String specimenImage,
                                                  String password, String ipAddress, String userAgent) {
        LearnerDocument document = learnerDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (document.getLearner() == null || !document.getLearner().getId().equals(learner.getId())) {
            throw new ResourceNotFoundException("Document not found");
        }
        if (document.getSha256() == null || document.getSha256().isBlank()) {
            throw new IllegalStateException("This document has no recorded hash and cannot be signed.");
        }
        SignatureEvent event = createEvent(SignableType.LEARNER_DOCUMENT, document.getId(), learner,
                document.getSha256(), LEARNER_DOCUMENT_DECLARATION, specimenImage, password, ipAddress, userAgent);
        return toResponse(event);
    }

    private SignatureEvent createEvent(SignableType type, Long signableId, Learner learner, String sha256,
                                        String declarationText, String specimenImage, String password,
                                        String ipAddress, String userAgent) {
        if (signatureEventRepository.findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(type, signableId).isPresent()) {
            throw new IllegalArgumentException("This has already been signed.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Re-enter your password to sign.");
        }
        // Re-fetched rather than trusting the caller's Learner instance to carry a password
        // hash: LearnerPrincipal (and by extension anything resolved from it) never does.
        Learner fresh = learnerRepository.findById(learner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Learner not found"));
        if (fresh.getPasswordHash() == null || !passwordEncoder.matches(password, fresh.getPasswordHash())) {
            throw new IllegalArgumentException("Password is incorrect.");
        }
        if (specimenImage == null || specimenImage.isBlank()) {
            throw new IllegalArgumentException("A drawn signature is required.");
        }

        SignatureEvent event = signatureEventRepository.save(SignatureEvent.builder()
                .signableType(type)
                .signableId(signableId)
                .signerRole("LEARNER")
                .signerId(learner.getId())
                .declarationText(declarationText)
                .specimenImage(specimenImage)
                .documentSha256(sha256)
                .signedAt(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 500)))
                .verificationCode(UUID.randomUUID().toString().replace("-", ""))
                .build());

        log.info("Signature {} recorded: {} #{} signed by learner {} (code {}).",
                event.getId(), type, signableId, learner.getLearnerCode(), event.getVerificationCode());

        self.stampAsync(event.getId());
        return event;
    }

    // ================================================================== revocation

    /**
     * Withdraws whatever active signature exists on one row. Called when a resubmission or a
     * new document version supersedes what an earlier signature attested to — the row being
     * signed still exists exactly as it was signed, but it is no longer the learner's current
     * work, so the signature attesting to it is withdrawn rather than left standing
     * unqualified. The one field this ever changes on an existing {@link SignatureEvent}
     * besides {@code stampedFilePath}; every other field stays exactly as recorded at signing.
     */
    @Transactional
    public void revokeActiveSignature(SignableType type, Long signableId, String reason) {
        signatureEventRepository.findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(type, signableId)
                .ifPresent(event -> {
                    event.setRevokedAt(LocalDateTime.now());
                    event.setRevokedReason(reason);
                    signatureEventRepository.save(event);
                    log.info("Signature {} revoked: {} #{} ({}).", event.getId(), type, signableId, reason);
                });
    }

    // ================================================================== reading (own)

    @Transactional(readOnly = true)
    public SignatureResponse requireOwn(Long eventId, Long learnerId) {
        SignatureEvent event = signatureEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature not found"));
        if (!"LEARNER".equals(event.getSignerRole()) || !event.getSignerId().equals(learnerId)) {
            throw new ResourceNotFoundException("Signature not found");
        }
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public SignatureResponse activeFor(SignableType type, Long signableId, Long learnerId) {
        SignatureEvent event = signatureEventRepository
                .findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(type, signableId)
                .orElseThrow(() -> new ResourceNotFoundException("Not signed"));
        if (!event.getSignerId().equals(learnerId)) {
            throw new ResourceNotFoundException("Not signed");
        }
        return toResponse(event);
    }

    private SignatureResponse toResponse(SignatureEvent event) {
        return SignatureResponse.builder()
                .id(event.getId())
                .signableType(event.getSignableType().name())
                .signableId(event.getSignableId())
                .signerRole(event.getSignerRole())
                .signedAt(event.getSignedAt())
                .verificationCode(event.getVerificationCode())
                .revoked(event.getRevokedAt() != null)
                .revokedAt(event.getRevokedAt())
                .revokedReason(event.getRevokedReason())
                .certificateReady(event.getStampedFilePath() != null && !event.getStampedFilePath().isBlank())
                .build();
    }

    // ================================================================== public verification

    /**
     * The whole public contract: document type, signer role, signature date, and whether the
     * hash still matches. Compared against the {@code sha256} column on the underlying row
     * right now, not by re-fetching and re-hashing the file — the same file-fetch avoidance as
     * signing, and it matters more here: this endpoint is unauthenticated, so a version that
     * fetched a file on every hit would be an open door to hammering storage with no login
     * required. A row whose current hash differs from what was signed, or that has vanished
     * entirely, answers {@code hashMatches = false}.
     */
    @Transactional(readOnly = true)
    public VerifyResponse verify(String verificationCode) {
        SignatureEvent event = signatureEventRepository.findByVerificationCode(verificationCode)
                .orElseThrow(() -> new ResourceNotFoundException("No signature found for this code."));

        String currentSha256 = currentSha256Of(event.getSignableType(), event.getSignableId());
        boolean hashMatches = currentSha256 != null && currentSha256.equals(event.getDocumentSha256());
        boolean revoked = event.getRevokedAt() != null;

        return VerifyResponse.builder()
                .documentType(documentTypeLabel(event))
                .signerRole(event.getSignerRole())
                .signedAt(event.getSignedAt())
                .hashMatches(hashMatches)
                .revoked(revoked)
                .valid(hashMatches && !revoked)
                .build();
    }

    private String currentSha256Of(SignableType type, Long signableId) {
        return switch (type) {
            case SUBMISSION -> submissionRepository.findById(signableId).map(Submission::getSha256).orElse(null);
            case LEARNER_DOCUMENT -> learnerDocumentRepository.findById(signableId).map(LearnerDocument::getSha256).orElse(null);
        };
    }

    private String documentTypeLabel(SignatureEvent event) {
        return switch (event.getSignableType()) {
            case SUBMISSION -> "Assignment Submission";
            case LEARNER_DOCUMENT -> learnerDocumentRepository.findById(event.getSignableId())
                    .map(LearnerDocument::getDocumentType)
                    .map(PoeDocumentType::getLabel)
                    .orElse("Learner Document");
        };
    }

    // ================================================================== certificate generation (async)

    /**
     * Dispatches onto the signature executor and returns immediately, with no HTTP request
     * bound to the thread that renders the certificate. Split from
     * {@link #generateAndStoreCertificate} for the same reason {@code PoeExportService} splits
     * its async entry point from its worker: a test can call the synchronous half directly, in
     * the same transaction as its fixture, without fighting an async worker racing the test's
     * own uncommitted setup.
     */
    @Async("signatureTaskExecutor")
    public void stampAsync(Long eventId) {
        self.generateAndStoreCertificate(eventId);
    }

    @Transactional
    public void generateAndStoreCertificate(Long eventId) {
        SignatureEvent event = signatureEventRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("Signature {} vanished before its certificate could be generated.", eventId);
            return;
        }
        try {
            byte[] pdf = renderCertificate(event);
            String path = storeCertificate(pdf, eventId);
            event.setStampedFilePath(path);
            signatureEventRepository.save(event);
            log.info("Signature certificate stored for event {} at {}.", eventId, path);
        } catch (Exception e) {
            // A missing certificate is a missing convenience, not a missing signature — the
            // event itself is already durable. Logged so it can be investigated, never
            // retried automatically onto a growing backlog.
            log.error("Could not generate signature certificate for event {}.", eventId, e);
        }
    }

    private byte[] renderCertificate(SignatureEvent event) throws IOException, WriterException {
        String verifyUrl = (publicBaseUrl == null || publicBaseUrl.isBlank() ? "" : stripTrailingSlash(publicBaseUrl))
                + "/verify/" + event.getVerificationCode();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float margin = 60;
            float width = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - margin;

            BufferedImage qr = renderQrCode(verifyUrl, 180);
            PDImageXObject qrImage = LosslessFactory.createFromImage(doc, qr);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                y = writeLine(cs, bold, 16, margin, y, "Digital Signature Certificate");
                y -= 10;
                y = writeLine(cs, font, 10, margin, y, "Document type: " + documentTypeLabel(event));
                y = writeLine(cs, font, 10, margin, y, "Signed by role: " + event.getSignerRole());
                y = writeLine(cs, font, 10, margin, y, "Signed at: " + event.getSignedAt());
                y = writeLine(cs, font, 10, margin, y, "Document SHA-256: " + event.getDocumentSha256());
                y = writeLine(cs, font, 10, margin, y, "Verification code: " + event.getVerificationCode());
                y -= 10;
                y = writeLine(cs, bold, 11, margin, y, "Declaration");
                y = writeWrapped(cs, font, 10, margin, y, width - 2 * margin, event.getDeclarationText());
                y -= 20;

                cs.drawImage(qrImage, margin, y - 180, 180, 180);
                cs.beginText();
                cs.setFont(font, 8);
                cs.newLineAtOffset(margin, y - 195);
                cs.showText(sanitizeForPdf("Scan to verify, or visit: " + verifyUrl));
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private BufferedImage renderQrCode(String content, int size) throws WriterException {
        var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private float writeLine(PDPageContentStream cs, PDFont font, float fontSize, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitizeForPdf(text));
        cs.endText();
        return y - (fontSize + 6);
    }

    private float writeWrapped(PDPageContentStream cs, PDFont font, float fontSize, float x, float y, float maxWidth, String text) throws IOException {
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        float current = y;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float lineWidth = font.getStringWidth(sanitizeForPdf(candidate)) / 1000 * fontSize;
            if (lineWidth > maxWidth && !line.isEmpty()) {
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

    private String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String storeCertificate(byte[] pdf, Long eventId) throws IOException {
        String filename = "signature_certificate_" + eventId + ".pdf";
        if (cloudinaryService.isConfigured()) {
            return cloudinaryService.uploadLearnerFile(pdf, filename);
        }
        Path dir = Paths.get(privateDir, "signatures").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);
        Files.copy(new java.io.ByteArrayInputStream(pdf), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    // ================================================================== certificate download (own)

    /** The stamped certificate's bytes, but only for the learner who signed it. */
    @Transactional(readOnly = true)
    public byte[] readOwnCertificate(Long eventId, Long learnerId) {
        SignatureEvent event = signatureEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Signature not found"));
        if (!"LEARNER".equals(event.getSignerRole()) || !event.getSignerId().equals(learnerId)) {
            throw new ResourceNotFoundException("Signature not found");
        }
        if (event.getStampedFilePath() == null || event.getStampedFilePath().isBlank()) {
            throw new ResourceNotFoundException("The certificate for this signature has not finished generating yet.");
        }
        return storedFileService.readBytes(event.getStampedFilePath(), "signature certificate");
    }
}
