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
import com.example.learnerassignments.service.ChatbotService;
import com.example.learnerassignments.service.LearnerService;
import com.example.learnerassignments.service.MessageService;
import com.example.learnerassignments.service.ModuleService;
import com.example.learnerassignments.model.LearnerDocument;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.model.ReviewStatus;
import com.example.learnerassignments.service.LearnerDocumentService;
import com.example.learnerassignments.service.SubmissionService;
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
    private final ChatbotService chatbotService;
    private final LearnerDocumentService documentService;
    private final LearnerTokenService tokenService;
    private final LecturerRepository lecturerRepository;

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

    // --- Assistant ---

    @PostMapping("/chatbot/ask")
    public ResponseEntity<Map<String, String>> askChatbot(@RequestBody Map<String, String> payload) {
        LearnerPrincipal principal = currentLearner.require();
        String query = payload.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("response", "Please ask a question."));
        }
        // The learner code comes from the session, not the request body — the assistant
        // answers with deadlines and submission state, which is one learner's own record.
        return ResponseEntity.ok(Map.of("response", chatbotService.generateResponse(query, principal.learnerCode())));
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
