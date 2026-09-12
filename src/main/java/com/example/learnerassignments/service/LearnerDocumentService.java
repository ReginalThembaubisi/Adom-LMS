package com.example.learnerassignments.service;

import com.example.learnerassignments.exception.InvalidFileException;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LearnerDocument;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.model.NotificationType;
import com.example.learnerassignments.model.ReviewStatus;
import com.example.learnerassignments.model.SignableType;
import com.example.learnerassignments.repository.LearnerDocumentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The learner's own documents: CV, ID copy, matric certificate, learnership agreement, and
 * anything else they need to attach.
 *
 * Learners supply these themselves. There is deliberately no bulk path for an admin to upload
 * them on a learner's behalf — for a forty-learner cohort that is around a hundred and sixty
 * uploads someone would otherwise be doing by hand, and the person who owns the document is
 * the person best placed to supply it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearnerDocumentService {

    private final LearnerDocumentRepository documentRepository;
    private final LearnerRepository learnerRepository;
    private final CloudinaryService cloudinaryService;
    private final StoredFileService storedFileService;
    private final NotificationService notificationService;
    private final SignatureService signatureService;

    /** Documents are personal, so they never go in the publicly served uploads directory. */
    @Value("${file.submission-dir:private-uploads}")
    private String privateDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "docx");
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * Records a new version of one of a learner's documents.
     *
     * Never overwrites. The previous version is marked superseded and kept, so a document that
     * was rejected and resupplied still shows what was sent the first time — which is the
     * history a SETA audit asks for, and the history a learner needs to see to understand why
     * they are being asked again.
     */
    @Transactional
    public LearnerDocument upload(Learner learner, PoeDocumentType documentType, MultipartFile file,
                                  String uploadedByRole) {
        validate(file);

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());

        // Hash the bytes as received, before storage can touch them.
        String sha256 = ContentHash.of(file);
        String storedPath = store(learner, documentType, file, originalFilename);

        // Supersede rather than replace.
        List<LearnerDocument> superseded = documentRepository.findCurrentForType(learner.getId(), documentType);
        superseded.forEach(previous -> previous.setCurrent(false));

        int nextVersion = documentRepository.findHighestVersion(learner.getId(), documentType).orElse(0) + 1;

        LearnerDocument document = documentRepository.save(LearnerDocument.builder()
                .learner(learner)
                .documentType(documentType)
                .filePath(storedPath)
                .originalFilename(originalFilename)
                .sha256(sha256)
                .version(nextVersion)
                .current(true)
                .status(ReviewStatus.PENDING)
                .uploadedAt(LocalDateTime.now())
                .uploadedByRole(uploadedByRole)
                .build());

        // A signature on a superseded version attested to bytes that are no longer the
        // learner's current work — withdraw it rather than leave it standing unqualified
        // against a document that has since been replaced. See SignatureService's class doc.
        superseded.forEach(previous -> signatureService.revokeActiveSignature(
                SignableType.LEARNER_DOCUMENT, previous.getId(), "Superseded by version " + nextVersion + "."));

        log.info("Learner document stored: learner {} supplied {} version {} ({} superseded).",
                learner.getLearnerCode(), documentType, nextVersion, superseded.size());
        return document;
    }

    /** Every version this learner has supplied, newest first, superseded ones included. */
    @Transactional(readOnly = true)
    public List<LearnerDocument> listFor(Long learnerId) {
        return documentRepository.findByLearner_IdOrderByUploadedAtDesc(learnerId);
    }

    /**
     * One document, but only if it belongs to this learner.
     *
     * Someone else's document is reported as not found rather than forbidden: a 403 would
     * confirm the id exists, which is all an attacker needs to know that a given learner has
     * uploaded their ID copy.
     */
    @Transactional(readOnly = true)
    public LearnerDocument requireOwnedBy(Long documentId, Long learnerId) {
        return documentRepository.findByIdAndLearner_Id(documentId, learnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));
    }

    @Transactional(readOnly = true)
    public LearnerDocument require(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));
    }

    /** Accept or reject, with a note the learner is shown as written. */
    @Transactional
    public LearnerDocument review(Long documentId, ReviewStatus status, String note, String reviewedBy) {
        if (status != ReviewStatus.ACCEPTED && status != ReviewStatus.REJECTED) {
            throw new InvalidFileException("A review decision must be ACCEPTED or REJECTED.");
        }
        if (status == ReviewStatus.REJECTED && (note == null || note.isBlank())) {
            // A rejection with no reason sends the learner back to a screen that tells them to
            // try again and nothing else.
            throw new InvalidFileException("Say why it was rejected — the learner is shown this note.");
        }

        LearnerDocument document = require(documentId);
        document.setStatus(status);
        document.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        document.setReviewedAt(LocalDateTime.now());
        document.setReviewedBy(reviewedBy);

        // Only a rejection is worth telling them about: an acceptance needs nothing from them,
        // and a badge that lights up for things requiring no action stops being read.
        //
        // The reviewer's decision above is the thing this call exists to record; telling the
        // learner about it is secondary and must never be able to undo it. notifyLearner runs
        // in its own transaction for exactly this reason, but that only protects this method's
        // own writes from a failure inside it — it does not stop that failure being *thrown*,
        // which would otherwise still fail this whole call and report the review itself as
        // unsaved. Caught and logged here so a notification problem is diagnosable without ever
        // being the reason a reviewer's decision is lost.
        if (status == ReviewStatus.REJECTED) {
            try {
                notificationService.notifyLearner(
                        document.getLearner(),
                        NotificationType.DOCUMENT_REJECTED,
                        "DOCUMENT", document.getId(),
                        // The reviewer's own words. Being told a document was rejected without
                        // the reason means guessing, re-uploading the same thing, and being
                        // rejected again.
                        "Your " + document.getDocumentType().getLabel() + " needs to be uploaded again: "
                                + document.getReviewNote());
            } catch (Exception e) {
                log.error("Document {} was rejected but the learner could not be notified: {}",
                        document.getId(), e.getMessage(), e);
            }
        }
        return document;
    }

    /** The stored bytes, for serving through an ownership-checked endpoint. */
    @Transactional(readOnly = true)
    public Object load(LearnerDocument document) {
        // Legacy public URL, authenticated public_id or a path on disk — StoredFileService
        // tells them apart. Documents uploaded before this phase keep opening unchanged.
        return storedFileService.open(document.getFilePath(), "document");
    }

    public String resolveContentType(String originalFilename) {
        return switch (extensionOf(originalFilename)) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Choose a file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidFileException("That file is larger than 10 MB. Please upload a smaller copy.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException("Accepted formats are PDF, JPG, PNG and DOCX.");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String store(Learner learner, PoeDocumentType documentType, MultipartFile file,
                         String originalFilename) {
        if (cloudinaryService.isConfigured()) {
            try {
                // The public_id, not the URL. An ID copy or a signed learner agreement is the
                // most sensitive thing this system holds; storing a public URL for one means
                // anyone who ever sees that URL can read it, logged in or not, forever.
                return cloudinaryService.uploadLearnerFile(file);
            } catch (IOException e) {
                // Log the cause. This used to discard it, which meant a learner saw "please try
                // again", the logs said nothing at all, and the only way to find out why the
                // vault was rejecting documents was to guess. The learner-facing message stays
                // generic — storage errors are not theirs to act on — but the reason has to
                // land somewhere.
                log.error("Could not upload {} for learner {} to Cloudinary.",
                        documentType, learner.getLearnerCode(), e);
                throw new InvalidFileException("That file could not be uploaded. Please try again.");
            }
        }

        // Local fallback, outside the statically served directory — these are somebody's ID
        // document, and the uploads directory is mapped as a public resource handler.
        Path directory = Paths.get(privateDir, "documents").toAbsolutePath().normalize();
        String storedName = String.format("%s_%s_%d_%s",
                learner.getLearnerCode(), documentType.name(), System.currentTimeMillis(), originalFilename);
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toString();
        } catch (IOException e) {
            log.error("Could not write {} for learner {} to {}.",
                    documentType, learner.getLearnerCode(), directory, e);
            throw new InvalidFileException("That file could not be saved. Please try again.");
        }
    }

}
