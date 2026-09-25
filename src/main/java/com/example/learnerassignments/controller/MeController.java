package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.model.SenderType;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.security.CurrentLearner;
import com.example.learnerassignments.security.LearnerPrincipal;
import com.example.learnerassignments.security.LearnerTokenService;
import com.example.learnerassignments.service.LearnerService;
import com.example.learnerassignments.service.MessageService;
import com.example.learnerassignments.service.ModuleService;
import com.example.learnerassignments.model.LearnerDocument;
import com.example.learnerassignments.model.ModuleFile;
import com.example.learnerassignments.model.SubmissionSession;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.model.ReviewStatus;
import com.example.learnerassignments.service.LearnerDocumentService;
import com.example.learnerassignments.service.NotificationService;
import com.example.learnerassignments.service.NotificationStream;
import com.example.learnerassignments.service.StoredFileService;
import com.example.learnerassignments.service.SubmissionService;
import com.example.learnerassignments.service.SignatureService;
import com.example.learnerassignments.dto.SignatureDtos;
import com.example.learnerassignments.model.SignableType;
import com.example.learnerassignments.exception.InvalidFileException;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Everything a signed-in learner can reach.
 *
 * The learner is always taken from the authenticated principal. No method on this
 * controller accepts a learner code, id or name as an argument — the caller cannot name
 * whose record they want, only ask for their own.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final CurrentLearner currentLearner;
    private final LearnerService learnerService;
    private final ModuleService moduleService;
    private final MessageService messageService;
    private final SubmissionService submissionService;
    private final StoredFileService storedFileService;
    private final NotificationService notificationService;
    private final NotificationStream notificationStream;
    private final LearnerDocumentService documentService;
    private final LearnerTokenService tokenService;
    private final LecturerRepository lecturerRepository;
    private final SignatureService signatureService;

    // --- Identity ---

    @GetMapping
    public ResponseEntity<LearnerResponse> getProfile() {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(learnerService.getLearnerByCode(principal.learnerCode()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            tokenService.revoke(header.substring("Bearer ".length()).trim());
        }
        return ResponseEntity.noContent().build();
    }

    // --- Coursework ---

    @GetMapping("/modules")
    public ResponseEntity<List<ModuleResponseDto>> getModules() {
        return ResponseEntity.ok(moduleService.getEnrolledModules(currentLearner.require().learnerCode()));
    }

    @GetMapping("/modules/{moduleId}")
    public ResponseEntity<ModuleDetailResponseDto> getModuleDetails(@PathVariable Long moduleId) {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(moduleService.getModuleDetailsForLearner(moduleId, principal.learnerCode()));
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<TimelineResponseDto>> getTimeline() {
        return ResponseEntity.ok(moduleService.getLearnerTimeline(currentLearner.require().learnerCode()));
    }

    // --- Submissions ---

    @GetMapping("/submissions")
    public ResponseEntity<List<StudentSubmissionHistoryDto>> getSubmissions() {
        return ResponseEntity.ok(learnerService.getLearnerSubmissions(currentLearner.require().learnerCode()));
    }

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> submitAssignment(
            @RequestParam("session_id") Long sessionId,
            @RequestParam("file") MultipartFile file) {

        LearnerPrincipal principal = currentLearner.require();
        SubmissionResponse response = submissionService.submitAssignment(principal.learnerCode(), sessionId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // The portal fetches submission files with an Authorization header and renders them from
    // an object URL, so there is no endpoint here that hands out a URL-embeddable credential.
    // ViewTicketService itself is kept — Phase 8 emails an export link, and a short-lived
    // signature scoped to one resource is the right primitive for that.

    // --- Personal documents ---
    //
    // Learners supply these themselves. There is deliberately no path for an admin to upload
    // on their behalf: for a forty-learner cohort that is around a hundred and sixty uploads
    // done by hand, and the person who owns the document is the one who has it.

    /** Everything this learner has been told, newest first. */
    @GetMapping("/notifications")
    public ResponseEntity<?> getMyNotifications() {
        LearnerPrincipal principal = currentLearner.require();
        List<NotificationDto> items = notificationService.listFor(principal.learnerId()).stream()
                .map(n -> NotificationDto.builder()
                        .id(n.getId())
                        .type(n.getType() != null ? n.getType().name() : null)
                        .refType(n.getRefType())
                        .refId(n.getRefId())
                        .body(n.getBody())
                        .createdAt(n.getCreatedAt())
                        .read(n.getReadAt() != null)
                        .build())
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("unread", notificationService.unreadCount(principal.learnerId()));
        body.put("items", items);
        return ResponseEntity.ok(body);
    }

    /** Just the badge, for a portal that only wants the number. */
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<?> getMyUnreadNotificationCount() {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(java.util.Map.of("unread", notificationService.unreadCount(principal.learnerId())));
    }

    /**
     * Clears the badge.
     *
     * Scoped to the learner the request authenticated as, never an id in the path — otherwise
     * clearing somebody else's badge is one request away, and they would never know why they
     * missed their result.
     */
    @PostMapping("/notifications/read")
    public ResponseEntity<?> markMyNotificationsRead() {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(java.util.Map.of("markedRead", notificationService.markAllRead(principal.learnerId())));
    }

    /**
     * Live push for an open portal.
     *
     * Carries no content — it says "go and look", and the client fetches through the
     * authenticated endpoints above. That keeps one place deciding what a learner may see, and
     * means a stale connection can never deliver something the learner should no longer have.
     *
     * The client also polls. This is an optimisation over polling, not a replacement for it.
     */
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamMyNotifications() {
        LearnerPrincipal principal = currentLearner.require();
        SseEmitter emitter = notificationStream.subscribe(principal.learnerId(), NotificationService.LEARNER_ROLE);
        if (emitter == null) {
            // Switched off. 503 rather than 404 because the endpoint exists and may be back:
            // the portal treats any non-OK as "keep polling" and retries in a minute, so
            // turning it on again needs no client change and no redeploy.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(emitter);
    }

    /** Released marking only. A draft or an internal report is not reachable from here. */
    @GetMapping("/feedback")
    public ResponseEntity<List<LearnerFeedbackDto>> getMyFeedback() {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(learnerService.getReleasedFeedback(principal.learnerCode()));
    }

    @GetMapping("/documents")
    public ResponseEntity<LearnerDocumentDtos.MyDocumentsResponse> getMyDocuments() {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(buildMyDocuments(documentService.listFor(principal.learnerId())));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LearnerDocumentDtos.DocumentResponse> uploadMyDocument(
            @RequestParam("document_type") String documentType,
            @RequestParam("file") MultipartFile file) {

        Learner learner = currentLearner.requireLearner();
        LearnerDocument saved = documentService.upload(learner, parseType(documentType), file, "LEARNER");
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * A learner's own document, streamed after the ownership check.
     *
     * Someone else's reads as not found. A 403 would confirm the id exists, which is enough to
     * learn that a named learner has uploaded their ID document.
     */
    @GetMapping("/documents/{documentId}/view")
    public ResponseEntity<?> viewMyDocument(@PathVariable Long documentId) {
        LearnerPrincipal principal = currentLearner.require();
        LearnerDocument document = documentService.requireOwnedBy(documentId, principal.learnerId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        documentService.resolveContentType(document.getOriginalFilename())))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getOriginalFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .body(documentService.load(document));
    }

    /**
     * A module file (facilitator guide, slides, notes) served through this application.
     *
     * The frontend used to send the browser straight to the stored URL. That produced a
     * download named after the Cloudinary public_id, which by design carries no file
     * extension — the raw/PDF delivery restriction keys off a recognised format, so files are
     * stored under an extension-less id and the real name is kept here instead. The result was
     * a learner downloading "1789044791251_Practical_2_-_LSUMS_IoT..." with no type, which
     * Windows cannot open. Serving it here puts the real filename back on the response.
     *
     * It also means course material stops being fetched by the learner's browser from a third
     * party, and stops being readable by anyone holding the URL.
     */
    @GetMapping("/module-files/{fileId}/download")
    public ResponseEntity<?> downloadModuleFile(@PathVariable Long fileId) {
        LearnerPrincipal principal = currentLearner.require();
        ModuleFile file = moduleService.requireModuleFileForLearner(fileId, principal.learnerCode());

        String filename = firstNonBlank(file.getOriginalFilename(), file.getTitle(), "document");
        return fileResponse(file.getFilePath(), filename, "file");
    }

    /** The task brief attached to a session, on the same terms. */
    @GetMapping("/sessions/{sessionId}/brief")
    public ResponseEntity<?> downloadSessionBrief(@PathVariable Long sessionId) {
        LearnerPrincipal principal = currentLearner.require();
        SubmissionSession session = moduleService.requireSessionForLearner(sessionId, principal.learnerCode());

        if (session.getTaskFilePath() == null || session.getTaskFilePath().isBlank()) {
            throw new ResourceNotFoundException("This session has no brief attached.");
        }
        String filename = firstNonBlank(session.getTaskFileName(), "brief");
        return fileResponse(session.getTaskFilePath(), filename, "brief");
    }

    /**
     * Fetch-and-re-serve, with the filename the facilitator uploaded.
     *
     * Content-Disposition is "attachment" because both of these are things a learner saves and
     * opens in Word or a PDF reader, not things the portal renders. The extension on that
     * filename is what makes the saved file openable.
     */
    private ResponseEntity<?> fileResponse(String storedPath, String filename, String description) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documentService.resolveContentType(filename)))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .body(storedFileService.open(storedPath, description));
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "document";
    }

    // --- Signatures ---
    //
    // A learner signs their own submission or document: declaration checkbox, drawn specimen,
    // password re-entry. The signature event itself is the record of record; the certificate
    // PDF (with its QR code) is generated afterwards on a background worker and may not be
    // ready the instant this call returns — see SignatureService's class doc for why.

    /** The exact wording the signing screen shows before the checkbox — never hardcoded client-side. */
    @GetMapping("/signatures/declaration-text")
    public ResponseEntity<Map<String, String>> declarationText(@RequestParam("signableType") String signableType) {
        return ResponseEntity.ok(Map.of("text", signatureService.declarationTextFor(parseSignableType(signableType))));
    }

    @PostMapping("/signatures")
    public ResponseEntity<SignatureDtos.SignatureResponse> sign(
            @RequestBody SignatureDtos.SignRequest request, HttpServletRequest httpRequest) {
        Learner learner = currentLearner.requireLearner();
        SignableType type = parseSignableType(request.getSignableType());
        String ip = clientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        SignatureDtos.SignatureResponse response = switch (type) {
            case SUBMISSION -> signatureService.signSubmission(learner, request.getSignableId(),
                    request.getSpecimenImage(), request.getPassword(), ip, userAgent);
            case LEARNER_DOCUMENT -> signatureService.signLearnerDocument(learner, request.getSignableId(),
                    request.getSpecimenImage(), request.getPassword(), ip, userAgent);
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Whether — and how — this learner has already signed a given row. 404 if not signed. */
    @GetMapping("/signatures/active")
    public ResponseEntity<SignatureDtos.SignatureResponse> activeSignature(
            @RequestParam("signableType") String signableType, @RequestParam("signableId") Long signableId) {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(signatureService.activeFor(
                parseSignableType(signableType), signableId, principal.learnerId()));
    }

    @GetMapping("/signatures/{signatureId}")
    public ResponseEntity<SignatureDtos.SignatureResponse> getSignature(@PathVariable Long signatureId) {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(signatureService.requireOwn(signatureId, principal.learnerId()));
    }

    @GetMapping("/signatures/{signatureId}/certificate")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long signatureId) {
        LearnerPrincipal principal = currentLearner.require();
        byte[] pdf = signatureService.readOwnCertificate(signatureId, principal.learnerId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"signature_certificate.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .body(pdf);
    }

    private SignableType parseSignableType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidFileException("signableType is required.");
        }
        try {
            return SignableType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException("Unknown signable type: " + raw);
        }
    }

    /** Render's proxy sets X-Forwarded-For; the direct remote address is the fallback. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private PoeDocumentType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidFileException("Say which document this is.");
        }
        try {
            return PoeDocumentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException("Unknown document type: " + raw);
        }
    }

    /**
     * The four required slots, whether or not anything has been supplied.
     *
     * Built as slots rather than as a list of what exists, so a missing document shows up as
     * an empty slot the learner can act on instead of simply not being there.
     */
    private LearnerDocumentDtos.MyDocumentsResponse buildMyDocuments(List<LearnerDocument> all) {
        List<LearnerDocumentDtos.DocumentSlot> slots = new java.util.ArrayList<>();
        int accepted = 0;

        for (PoeDocumentType type : PoeDocumentType.required()) {
            List<LearnerDocument> ofType = all.stream()
                    .filter(d -> d.getDocumentType() == type)
                    .toList();

            // "Not superseded" rather than "current is true": a null would drop the row from
            // this filter and the learner would be told their document is missing while the
            // row sits in the table. The column is NOT NULL, and this still does not rely on it.
            LearnerDocument current = ofType.stream()
                    .filter(d -> !Boolean.FALSE.equals(d.getCurrent()))
                    .max(java.util.Comparator.comparing(LearnerDocument::getVersion))
                    .orElse(null);

            if (current != null && current.getStatus() == ReviewStatus.ACCEPTED) {
                accepted++;
            }

            slots.add(LearnerDocumentDtos.DocumentSlot.builder()
                    .documentType(type.name())
                    .documentLabel(type.getLabel())
                    .poeSection(type.getPoeSection())
                    .required(type.isRequired())
                    .current(current == null ? null : toResponse(current))
                    .history(ofType.stream()
                            .filter(d -> current == null || !d.getId().equals(current.getId()))
                            .map(this::toResponse)
                            .toList())
                    .build());
        }

        List<LearnerDocumentDtos.DocumentResponse> other = all.stream()
                .filter(d -> d.getDocumentType() == PoeDocumentType.OTHER)
                .map(this::toResponse)
                .toList();

        return LearnerDocumentDtos.MyDocumentsResponse.builder()
                .slots(slots)
                .other(other)
                .requiredTotal(PoeDocumentType.required().size())
                .requiredAccepted(accepted)
                .build();
    }

    private LearnerDocumentDtos.DocumentResponse toResponse(LearnerDocument d) {
        return LearnerDocumentDtos.DocumentResponse.builder()
                .id(d.getId())
                .documentType(d.getDocumentType().name())
                .documentLabel(d.getDocumentType().getLabel())
                .poeSection(d.getDocumentType().getPoeSection())
                .required(d.getDocumentType().isRequired())
                .originalFilename(d.getOriginalFilename())
                .version(d.getVersion())
                .current(!Boolean.FALSE.equals(d.getCurrent()))
                .status(d.getStatus() == null ? ReviewStatus.PENDING.name() : d.getStatus().name())
                .reviewNote(d.getReviewNote())
                .reviewedAt(d.getReviewedAt())
                .uploadedAt(d.getUploadedAt())
                .uploadedByRole(d.getUploadedByRole())
                .build();
    }

    // --- Messages ---

    @GetMapping("/facilitators")
    public ResponseEntity<List<PersonSummaryDto>> getFacilitators() {
        Learner learner = currentLearner.requireLearner();
        return ResponseEntity.ok(messageService.getFacilitatorsForLearner(learner));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<MessageThreadSummaryDto>> getThreads() {
        Learner learner = currentLearner.requireLearner();
        return ResponseEntity.ok(messageService.getThreadSummariesForLearner(learner));
    }

    @GetMapping("/messages/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Learner learner = currentLearner.requireLearner();
        return ResponseEntity.ok(Map.of("unreadCount", messageService.getUnreadCountForLearner(learner)));
    }

    @GetMapping("/messages/{lecturerId}")
    public ResponseEntity<List<MessageDto>> getThread(@PathVariable Long lecturerId) {
        Learner learner = currentLearner.requireLearner();
        requireOwnFacilitator(lecturerId, learner);
        return ResponseEntity.ok(messageService.getThreadAndMarkRead(learner.getId(), lecturerId, SenderType.LEARNER));
    }

    @PostMapping("/messages/{lecturerId}")
    public ResponseEntity<MessageDto> sendMessage(@PathVariable Long lecturerId,
                                                  @Valid @RequestBody SendMessageRequest request) {
        Learner learner = currentLearner.requireLearner();
        Lecturer lecturer = requireOwnFacilitator(lecturerId, learner);
        MessageDto saved = messageService.sendMessage(learner, lecturer, SenderType.LEARNER, request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * A facilitator who teaches one of this learner's modules, or 404.
     *
     * Reported as not found rather than forbidden so the response cannot be used to probe
     * which lecturer ids exist.
     */
    private Lecturer requireOwnFacilitator(Long lecturerId, Learner learner) {
        if (!messageService.facilitatorOwnsLearner(lecturerId, learner)) {
            throw new ResourceNotFoundException("Facilitator not found");
        }
        return lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Facilitator not found"));
    }
}
